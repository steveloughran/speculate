# Java and Meltdown Attempt 1: fail


TL;DR: the code generated from Java source, in my limited experiment, isn't up a Meltdown attack, because
a second range check stops us doing the cache fetching trick needed to leave a trace from the speculation.
but: I think it's possible to come up with an alternative, and wouldn't get overconfident yet.

## Disclaimer

This is very much non-normative; it's based on a limited set of experiments on a mid 2009 macbook pro.

This does not show Meltdown at work in the JDK, only that some test runs of the Java 1.8.0_162-ea-b03 (server JRE)
running on MacOS 10.11.6 generated a sequence of operations which don't seem to do what I want, at least
from the source code.

I don't know enough about modern CPU branch prediction to conclude whether or not this is vulnerable
to meltdown. I had to learn x86-64 assembler to even make sense of what was being logged, which shows
how detached I've become from CPU parts. When I was writing Win32 C++ code, Visual Studio would show
the assembler as you debugged, and you did get used to reading the assembly, understanding it, and
knowing how it all glued together. Not any more. Now I use tests over edit-and-continue coding while
debugging the app, and don't even know the Java "Virtual Machine" assembler. 

1. If the CPU can speculate on a branch within a speculative branch, the exploit holds (as [asserted by Thomas Wuerthinger](https://twitter.com/thomaswue/status/950316107877027840)). In which case the I'm utterly wrong, Java code can be used.
1. Other JREs may generate different code.
1. The same JRE may choose to generate different code on a different run. Who knows?
1. The way the hotspot compiler works, it has to decide when to compile down.
You need a lot of iterations to do this; the number changed whenever I modified the inner method.
1. Because you can put arbitrary assembly language into a JNI library, you are guaranteed to be able
to implement an exploit there. They can  call cache flushing operations,
use `rdtsc` to measure cache lookup in cycles, etc, and make full use of things.

*if you can get a JNI library executed in a Java code, your chance of getting a meltdown exploit
in on an unmodified computer is 100%*. 

Finally, this says nothing about Spectre. Someone else gets to worry about that.
Assume it holds, and plan accordingly.


## Introduction

The Meltdown and Spectre attacks are fascinating because they aren't
software bugs, they are microprocessor-level issues.
More subtly, they are architectural issues related to speculative execution, which has been
the core means of delivering performance from x86 parts since the P6/Pentium Pro shipped
in 1995, clocked at 150 MHz(!) (it was a lot at time, especially if, like me, you had a twin-CPU workstation
running Windows NT4, an operating system which could run on an SMP multiprocessor system!).

What did the P6 core offer?

* Out of order execution: operations were executed as their inputs were ready, rather than in the sequence of
machine code passed in.
* Register shadowing: to avoid the limited number of x86 registers (8) crippling the OOO, a larger pool of registers
were implemented internally, which the OOO ops can use.
* Speculative execution: the CPU could execute a branch of a conditional statement, but not make the code
 "appear" to happen, until the outcome of the condition was resolved, at which point they are committed.
* Branch Prediction: improving the accuracy of guessing which way a branch will go using the previous history
of that branch.

And of course, more caching, with cached data remaing coherent across CPU sockets, somehow.

It's that speculation and branch prediction which the new attacks go after.

1. Meltdown: the value of speculative memory reads can be inferred.
1. Spectre: inferring information via the branch predictor.



## Experimental source, attempt 1

The (evoving) source is at: [Main.java](https://github.com/steveloughran/speculate/blob/master/src/main/java/org/apache/labs/Main.java)


```java
package org.apache.labs;

public class Main {

  private static final int ATTEMPTS = 8000;
  private static final int SOURCE_ARRAY_SIZE = 0xfeed;
  private static int[] data = new int[SOURCE_ARRAY_SIZE];
  private static final int REDIRECT_ARRAY_SIZE = 0x8000;
  private static int[] redirect = new int[REDIRECT_ARRAY_SIZE];

  public static void main(String[] args) {
    int attempts = ATTEMPTS;
    if (args.length > 0) {
      attempts = Integer.valueOf(args[0]);
    }

    for (int i = 0; i < REDIRECT_ARRAY_SIZE; i++) {
      redirect[i] = i;
    }

    for (int i = 0; i < SOURCE_ARRAY_SIZE; i++) {
      data[i] = i & 0xff;
    }
    for (int i = 0; i < attempts; i++) {
      int e = codeblock(i % REDIRECT_ARRAY_SIZE, data, redirect);
      System.out.printf("%d -> %d\n", i, e);
    }
  }

  private static int codeblock(int iter, int[] src, int[] redir) {
    int d = src[iter];
    int e = redir[0 +((d & 1) << 8)];
    return e;
  }
}

```

The exploit is in the `codeblock()` method, because it needs executing ~10K times before the JIT compiler
generates the x86 code.

I'm passing in the two buffers for memory lookup to avoid static references appearing, which complicated
reading the code.

This makes this little routine the core of the exploit

```java
  private static int codeblock(int iter, int[] src, int[] redir) {
    int d = src[iter];                    // line 32
    int e = redir[0 +((d & 1) << 8)];     // line 33
    return e;
  }

``` 

## Assembly version

It's surprisingly hard to get the assembly output from Java running on a macbook,
primarily because *every single link to the artifacts you need are dead*.
All blog posts on this topic from even 4 years ago give you hints, but then point at
some java.sun.com URL which resolves into a toplevel "we are oracle, we own java, be happy, buy our RDBMS" page.

By breaking all their old URLs, oracle have made JVM forensics significantly harder than it need be.


Once you have uncovered a copy of the DLL from somewhere, you can
Built with `maven install` or the IDE, and then executed with

```bash
java -XX:+UnlockDiagnosticVMOptions -XX:+PrintAssembly -jar target/speculation-1.0-SNAPSHOT.jar 10000 > target/out.asm 
```

This generates 6-7 MB of logs, where you can then look for the string `'codeblock'` to find the
compiled method somewhere near the tail of the log.

#### Method `codeblock()`

```
[Entry Point]
[Verified Entry Point]
[Constants]
# {method} {0x000000011c26ee08} 'codeblock' '(I[I[I)I' in 'org/apache/labs/Main'
# parm0:    rsi       = int
# parm1:    rdx:rdx   = '[I'
# parm2:    rcx:rcx   = '[I'
#           [sp+0x40]  (sp of caller)
0x00000001081aa600: mov    %eax,-0x14000(%rsp)
0x00000001081aa607: push   %rbp
0x00000001081aa608: sub    $0x30,%rsp
0x00000001081aa60c: movabs $0x11c26f5f0,%rax
0x00000001081aa616: mov    0x8(%rax),%edi
0x00000001081aa619: add    $0x8,%edi
0x00000001081aa61c: mov    %edi,0x8(%rax)
0x00000001081aa61f: movabs $0x11c26ee08,%rax  ;   {metadata({method} {0x000000011c26ee08}
        'codeblock' '(I[I[I)I' in 'org/apache/labs/Main')}
0x00000001081aa629: and    $0x3ff8,%edi
0x00000001081aa62f: cmp    $0x0,%edi
0x00000001081aa632: je     0x00000001081aa66a  ;*aload_1
```

  At this point, we have the three arguments in three different registers
  
  | reg        | value           | meaning  |
  | ------------- |:-------------:| -----|
  | `rsi` | `iter` | iterator value in outer loop. |
  | `rdx` | `data` | first array, the one we want to break out from |
  | `rcx` | `redir` | second array, where the TLB corruption is to be detected |
  
  
Now into the real code
  
#### Operation #1: MOV
   
  
```
0x00000001081aa638: movslq %esi,%rax
```

Takes the lowest 32 bits of `rsi` (the int32 value), [move with sign expansion](http://www.felixcloutier.com/x86/MOVSX:MOVSXD.html)
from int32 to int34 in `rax`. (`esi` is the extended 32 bit version of the 16 bit `si` register).


#### Operation #2: CMP

```
0x00000001081aa63b: cmp    0xc(%rdx),%esi     ; implicit exception: dispatches to 0x00000001081aa67e
```

[CMP](http://www.felixcloutier.com/x86/CMP.html): Compare `*[rdx+0xC]` with the value of `esi`, that is, `iter`. 

I don't know anything about Java object internals except that I do know that all the non-intrinsic fields
all have some bytes at the front, including reference count. We're looking at a range 12 bytes in,  comparing int32
value. 

Assumption: this is the range check; the header has at offset 0xC, base + 12, an int32 size. 

#### Operation #3: JAE 

```
0x00000001081aa63e: jae    0x00000001081aa688
```

[JAE](http://www.felixcloutier.com/x86/Jcc.html) Short jump if condition is `>= 0`. 

If the value of `iter` is >= the value in the header, the code jumps off,
presumably to raise an `ArrayOutOfBoundsException`.
But hat happens on negative values? They're out of range too, aren't they?
Well, `JAE` is unsigned, Jump-Above-or-Equal.
All negative offsets will have bit 32 set, so they are all automatically
going to be above the maximum length of an array (which is 2^31 -1). 
This is sweet: you get both the positive and negative range check in one operation. 

Now, if the jump condition is not met, the branch isn't taken, and the next opcode applies.

*This is the sequence which speculative execution can execute on an Intel CPU, even if the range check fails*

#### Operation #4: MOV, possibly speculative 

```
0x00000001081aa644: mov    0x10(%rdx,%rax,4),%eax  ;*iaload
                                    ; - org.apache.labs.Main::codeblock@2 (line 32)
```

This is it: the troublespot. A memory reference is taking place, which, in speculative mode, can happen ahead of
the branch if certain conditions are met:

1. The arguments are ready. The move to `rax` was triggered in operation #1, it's reg-reg, and with Operand 2 being
a memory reference, we can assume its ready.
1. Whatever execution units the CPU has for memory IO has capacity.
1. The branch is *predicted* as being taken. The history of branches at the same address are used here;
Spectre shows how, because many branch addresses can match the same entry in the branch predictor, malicious
code can contaminate the prediction, and/or use timings of its own work to infer what branch was taken.

As AMD parts don't to speculative memory accesses, they aren't going to execute this instruction until
the range check is complete. It sounds like some ARM parts do though.

#### Operation 5: AND
```
0x00000001081aa648: and    $0x1,%eax
```

This extracts the lowest bit of the data read. If the operation went ahead speculatively, then this is where the illegally
accessed data will be obtained.

#### Operation 6-7: SHL, MOVSLQ

```
0x00000001081aa64b: shl    $0x8,%eax
0x00000001081aa64e: movslq %eax,%rsi
```

Shift left the anded-bit by 8 bits, so giving an offset of 0 or 256 into the `redirect` buffer.

At this point, on an Intel part, this can still be speculating.

We're hoping that the size of a $L1 cache line is such that this will refer to an address in a different
cach line. This is just an example; I don't know what they are, but it's the thought that counts.

Up to this point, the generated code behaves exactly as needed for Meltdown.


#### Operation 8: CMP 

We're now trying to reference the next data buffer, `redir`, using an offset calculated from
the data read possibly illegally.

In C or C++, this would just be another pointer dereference.

Except, Java requires a range check on all array references. So the length of the offset is
compared to the value of the `*[redir +0xC]`. 

```
0x00000001081aa651: cmp    0xc(%rcx),%eax     ; implicit exception: dispatches to 0x00000001081aa691
```

This can still be executed if we're speculating. 

#### Operation 9: JAE 

I think this is where meltdown falls apart.

The results of the range check in operation 8 are examined, and if the index into the `redir` buffer is out
of range, it jumps off to raise an exception.

```
0x00000001081aa654: jae    0x00000001081aa69b
```

Unless the CPU can do a speculative branch inside a speculative branch, speculation is going
to have to block here.
That is: this branch has a Read-After-Read dependency on the previous branch, of some form or other.

If the initial index `iter` value was out of range of the `data` buffer, then when the CMP of operation 8
is completed, the subsequent jump will take *the other* branch, the one which raises an exception.

The speculatively executed operations will simply be discarded.

#### Operation 11: MOV

This is where the second buffer, `redir` is read using an offset calculated from the read of Operation #4.

```
0x00000001081aa65a: mov    0x10(%rcx,%rsi,4),%eax  ;*iaload
                                            ; - org.apache.labs.Main::codeblock@13 (line 33)
```

Were it not for that range check and its extra jump, this could be running speculatively, hence having the side
effect which meltdown exploits.
But provided the jump of operation #10 forces the CPU to block there
until the outcome of the jump at operation #8 is known,
this read here will never be executed before the range check of the first read has been completed.
Which, given an out of range index will have branched of already, means that you don't to do a speculative
read on the second array based on the unvalidated data of the first deref.

It may be executed speculatively if the index into the `redir[]` array read is out of range, but that's a separate issue.
you can't get here with data from the previous buffer, even specuatively. 

#### Everything else

Other stuff, returning back to the caller. 

```
0x00000001081aa65e: add    $0x30,%rsp
0x00000001081aa662: pop    %rbp
0x00000001081aa663: test   %eax,-0x640569(%rip)        # 0x0000000107b6a100
                                            ;   {poll_return}
0x00000001081aa669: retq  
```

There you have it. The code the JIT compiler generated for the double derefencing has a range check on each
lookup, and unless the CPU can do a speculative branch prediction inside another branch, it's not going
to do the second pointer dereference.

*The basic Meltdown example doesn't work in Java, but only because dereferencing into an array doesn't work
as an exfiltration strategy, and provided the CPU doesn't do deep-branch-preduction*

## Other solutios

It appears that the basic `array[offset]` strategy doesn't work as an exploit because of the guarded access
adding a second CMP/JAE pair of opcodes into the sequence. Is there any other strategy?

Well, we can't use anything else with a range check or similar, or anything with a conditional, can we?

Except: the CMOV, conditional move operation, isn't a branch, is it? It's a non-branching assignment which
is only applied if the condition holds. 

Something a bit like this:
```java
final static String srcA = "stringA";
final static String srcB = "stringB";

private static String attempt2(int iter, boolean[] src) {
String ref = srcB;
boolean d = src[iter];
ref = d ? srcA : srcB;
return ref;
}
```

if the ? : op is handled with a CMOV, then it could be executed within the speculation sequence,
which means that the next step of the exploit can be achieved, leaving the remainder: how to 
determine from cache lines which one was speculatively executed?

I have no idea: but if a conditional assignment does work --not something I've explored yet-- then
cache lines are going to be the final detail. And it'd be a detai, because the core part of the exploit
would have taken place.

FWIW, an initial test run here doesn't use CMOV, it does a branch.
But there are some OpenJDK bug reports which discuss the performance of CMOV vs branch,issues
which hint that the JIT compiler makes the decision on which to use based on
whatever it thinks is the most optimal:

* [Strange performance behaviour of cmov vs branch on x86](https://bugs.openjdk.java.net/browse/JDK-8034833)
* [Don't use Math.min/max intrinsic on x86](https://bugs.openjdk.java.net/browse/JDK-8039104)


Accordingly: *you can't conclude that from this one single experiment that CMOV doesn't get generated
in some situations*.
