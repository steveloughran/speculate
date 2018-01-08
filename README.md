# An experiment in generating meltdown friendly specex branches in Java

This does not show Meltdown at work in the JDK, only that some test runs of the Java 1.8.0_162-ea-b03 (server JRE)
running on MacOS 10.11.6 generated a sequence of operations which would appear to match what is needed.

1. Other JREs may generate different code.
1. The way the hotspot compiler works, it has to decide when to compile down.
You need a lot of iterations to do this; the number changed whenever I modified the inner method.
1. You still need the next step of the exploit, measuring the cache lookup times of array #2,
using that to infer which entry was read. I have no idea how to begin doing that in Java.
1. JNI libraries let you do this much more efficiently because you get full control of the assembly
language and can call cache flushing operations, use `rdtsc` to measure cache lookup in cycles, etc.

*if you can get a JNI library executed in a Java code, your chance of getting a meltdown exploit
in on an unmodified computer is 100%*. On 




DLL for macos from https://github.com/evolvedmicrobe/benchmarks/blob/master/hsdis-amd64.dylib
https://mechanical-sympathy.blogspot.co.uk/2013/06/printing-generated-assembly-code-from.html
https://meltdownattack.com/meltdown.pdf

```
set -gx LD_LIBRARY_PATH lib/
export LD_LIBRARY_PATH=~lib/


'-XX:CompileCommand=print,*org.apache.labs.Main.run' 
```

# Generating the Assembly

* [Speeding up JIT compilation](https://websphere4u.wordpress.com/2012/01/30/jit-compilation-of-java-code-wont-happen-before-10000-invocations-of-the-same-code-block/)

`-XComp` compiles everything, which includes all the bits of the JVM invoked.
Better to use `-XX:CompileThreshold=100`




# Notes

It's truly awful how pretty much every article and blog post on the topic of JIT compilation is broken.

Why? Oracle killed every java.sun link.

Gone: the OpenJDK source downloads needed to build the `hsdis_amd64.dylib` DLL.

Gone: Everything releated to [ On Stack Replacement](http://java.sun.com/developer/technicalArticles/Networking/HotSpot/onstack.html),
as referenced by [A close look at Java’s JIT (2012)](https://www.beyondjava.net/blog/a-close-look-at-javas-jit-dont-waste-your-time-on-local-optimizations/).

Low level information on how the JRE compiles code to native machine code is now lost, and now we are all struggling
to work out what's going on, because articles written six years go have had all their citations unlinked.
(there's some coverage on [github](https://github.com/AdoptOpenJDK/jitwatch/wiki/Understanding-the-On-Stack-Replacement-(OSR)-optimisation-in-the-HotSpot-C1-compiler)), 
better hope they and StackOverflow never go away.


## References

* [x86-64 Machine-Level Programming](https://www.cs.cmu.edu/~fp/courses/15213-s07/misc/asm64-handout.pdf)