# Sonatype Nexus OSS Maven Plugin (nexus-maven-plugin)


**DEPRECATED** **DISCONTINUED**

I've decided to discontinue this plugin due to several reasons. One main reason may be that I can't encourage projects to use Maven ;-)
But this functionality is still needed and it is now moved into a Plugin for [Jenkins](http://jenkins-ci.org). You can use the [artifact-promotion Plugin](https://wiki.jenkins-ci.org/display/JENKINS/ArtifactPromotionPlugin) in the same way as the Maven plugin.
Feel free to fork the [Github Repository](https://github.com/jenkinsci/artifact-promotion-plugin) and contribute your changes.

## Overview

This Apache Maven plugin is for moving artifacts from a origin repository 
to a target repository. The repositories can be on the same or different servers.

### Why?

Why we should do things like this? 
Maven's "SNAPSHOT-RELEASE" cycle doesn't support artifact handling needed in a 
Continuous Delivery environment (build always releases). In a CD environment, we
don't work with SNAPSHOT versions because we build always release candidates.
And during the CD Build Pipeline, we propagate artifacts through the stages.
"Enterprise Repository Servers" like Artifactory pro or Nexus Professional are 
supporting this by providing "staging repositories". This is not the
case in the Nexus OSS version and due to this we need a workaround.

## How it works

It's really simple and build on three steps:

- download the artifact from the origin repository using the maven-dependency-plugin
- upload the artifact into the target repository using the maven-deploy-plugin
- delete the artifact from the origin repository using the Nexus OSS REST interface

## Maven coordinates

The plugin is now available in the Maven Central repositorys.
The current Maven coordinates are:

```XML
    <groupId>com.github.hcguersoy</groupId>
    <artifactId>nexus-maven-plugin</artifactId>
    <version>0.3.1</version>
```

## Build from source

Build the jar file with 'mvn package' and deploy the war file into a Maven repository, 
either your local one (use mvn install instead of package) or into a repository server using
Maven CLI or using a CI Server like Jenkins.

## Usage

A very simple example:

```XML
        <profile>
            <id>promote-artifact</id>
            <build>
                <plugins>
                    <plugin>
                        <groupId>com.github.hcguersoy</groupId>
                        <artifactId>nexus-maven-plugin</artifactId>
                        <executions>
                            <execution>
                                <id>moveartifact</id>
                                <phase>validate</phase>
                                <goals>
                                    <goal>move</goal>
                                </goals>
                                <configuration>
                                    <groupId>foo.com</groupId>
                                    <artifactId>foo.project</artifactId>
                                    <version>1.0.0-256-aff45a</version>
                                    <packaging>war</packaging>
                                    <targetNexus>http://foo.com:8080</targetNexus>
                                    <targetRepoID>foo-releases</targetRepoID>
                                    <stagingRepoID>foo-candidates</stagingRepoID>
                                    <stagingNexus>http://foo.com:8080</stagingNexus>
                                </configuration>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
        </profile>
```

Activating this profile will do the job during the validate phase in maven.

More to come.

## Next steps

- fix and align dependency versions
- other repository servers should be supported 

I'm thinking about implementing this stuff as a Jenkins plugin. IMHO this task should not be defined in a Maven POM. 

An other idea is to implement an alternative "Maven Release Plugin for Continuous Delivery" using this plugin as a part of the workflow.

## Credits

Thanks to Tim Moore and Don Brown providing the 'Mojo Executor Plugin' to the community.
See more details on this plugin here: http://timmoore.github.io/mojo-executor/

Special thanks to Reinhard Nägele contributing all the stuff for releasing this plugin at Maven Central and hints.

## Supported Platforms

Nexus OSS 2.x
Maven 2/3

Contributions are welcome.

## License

Licensed under the Apache License, Version 2.0 (the "License"); 
you may not use this file except in compliance with the License. 
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed 
under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR 
CONDITIONS OF ANY KIND, either express or implied. See the License for the 
specific language governing permissions and limitations under the License.
