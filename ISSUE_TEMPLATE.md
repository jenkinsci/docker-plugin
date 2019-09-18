Firstly, please do not use the issue tracker to ask questions, join jenkins-users mailing list.

If you get some troubles with docker and Jenkins then you first need to determine which plugin you are using.
There is more than one plugin for Jenkins that provides docker-related capabilities, and this is just one of them.
See the [README.md](README.md) file for further details.

Note: The stacktrace(s) you see will also indicate which plugin is causing the issue.
If none of the classes mentioned in the exception stacktrace are classes from this plugin then it probably is not originating from this plugin.

Please read [the contribution guidelines](CONTRIBUTING.md) as well, as that's got a lot of useful information in there too that'll help people help you.

If you are sure that this plugin is the source of your troubles then please report:
 - [ ] docker-plugin version you use
 - [ ] jenkins version you use
 - [ ] docker engine version you use
 - [ ] details of the docker container(s) involved and details of how the docker-plugin is connecting to them
 - [ ] stack trace / logs / any technical details that could help diagnose this issue


Lastly, before submitting a bug report, remove this explanatory text, leaving just the important information.
