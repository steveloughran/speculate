

DLL for macos from https://github.com/evolvedmicrobe/benchmarks/blob/master/hsdis-amd64.dylib
https://mechanical-sympathy.blogspot.co.uk/2013/06/printing-generated-assembly-code-from.html
https://meltdownattack.com/meltdown.pdf

```
set -gx LD_LIBRARY_PATH lib/
export LD_LIBRARY_PATH=~lib/


'-XX:CompileCommand=print,*org.apache.labs.Main.run' 
```
