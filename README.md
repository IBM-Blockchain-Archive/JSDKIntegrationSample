# BMXHyperledgerFabricJSDKIntegrationSample

## Hyperledger Fabric Java SDK  Version
 This code is dependent on a 1.1.0-SNAPSHOT
 
 To reference this snapshot repository update your Maven settings .m2/settings.xml file with the below profile:
 
 
 ```
 <?xml version="1.0" encoding="UTF-8"?>
 <settings xmlns="http://maven.apache.org/SETTINGS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0                           https://maven.apache.org/xsd/settings-1.0.0.xsd">
    <profiles>
       <profile>
          <id>allow-snapshots</id>
          <activation>
             <activeByDefault>true</activeByDefault>
          </activation>
          <repositories>
             <repository>
                <id>snapshots-repo</id>
                <url>https://oss.sonatype.org/content/repositories/snapshots</url>
                <releases>
                   <enabled>false</enabled>
                </releases>
                <snapshots>
                   <enabled>true</enabled>
                </snapshots>
             </repository>
          </repositories>
       </profile>
    </profiles>
 </settings>


```

## Installing
 Run command:
  ```mvn install```
 
## Running

The code can not be run until a BlueMix Block Chain network has been created.  See video for details. 

Run command: 
```mvn exec:java -Dexec.mainClass="org.cr22rc.BMXHyperledgerFabricJSDKIntegrationSample"```

## Introduction Video
An introduction video is in Vidoes/bmxJSDKIntro.webm

## Video Screen Shots
Screen shots are in screenShots directory