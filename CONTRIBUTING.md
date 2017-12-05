# Creating new issue

- try reproduce with `jenkins/(ssh-slave|jnlp-slave|slave)` image with dumb freestyle job
- provide full description of issue
- check `Manage Jenkins` -> `Manage Old Data` for not updated configuraiton and provide this info
- provide full list of installed plugins with versions, jenkins core version
- check and provide errors from system jenkins.log and errors from `Manage Jenkins` -> `System Log`
- provide Cloud section from $JENKINS_HOME/config.xml file
- describe how your docker infrastructure looks like
- provide docker host/api version
- provide steps for reproducing
- any code or log example surround with right markdown https://help.github.com/articles/github-flavored-markdown/

# For pull requests

- Refer to some existed issue or provide description like for issue

# Links

- https://wiki.jenkins-ci.org/display/JENKINS/Beginners+Guide+to+Contributing
- https://wiki.jenkins-ci.org/display/JENKINS/Extend+Jenkins
