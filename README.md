This document shows how to integrate the IBM hyperledger Java SDK with IBM bluemix blockchain. To use Bluemix IBM Blockchain Service, follow https://github.com/IBM-Blockchain/marbles/blob/v4.0/docs/use_bluemix_hyperledger.md to create a Blockchain Network in IBM Bluemix, peers (up to 3) and a channel. 

## Hyperledger Fabric Java SDK  Version
 Requires Hyperledger Fabric Java SDK 1.1.0-SNAPSHOT
 
## Down load the repository 
git clone https://github.com/IBM-Blockchain/JSDKIntegrationSample

## Obtain "Service Credential" from bluemix
From your bluemix dashboard Overview, click on "Service Credentials", copy and paste all the information to a file named "bmxServiceCredentials.json". Save this file to the location where you download the repository

![serviceCredentials](images/serviceCredential.png)

## Modify BMXHyperledgerFabricJSDKIntegrationSample.java
Two places need to be changed:
NETWORK_CONFIG_PEERORG_CA: get network ca information from bmxServiceCredentials.json

TEST_CHANNEL: update with your own channel name.

![javeSampleCode](images/javaSampleCode.png)

## Installing
 Run command:
  ```mvn install```
  
 Issue “mvn install” from your download folder, confirm with “BUILD SUCCESS” message
 
 ![installBuild](images/installBuild.png)
 
## Running

Run command: 
```mvn exec:java -Dexec.mainClass="org.cr22rc.BMXHyperledgerFabricJSDKIntegrationSample"```

It will generate the cert for user "admin" at the first time run, the credential info will be saved to "bmxBlockChainSampleStore.properties".

![installBuild](images/execution.png)

## Upload the certification to bluemix

Copy and paste CERTFICATE from java test case output to bluemix web GUI

![memberView](images/member.png)
![addCert](images/addCert.png)

Tip: if the submit button is disabled, just add CR to ------END CERTIFICATE-----

## Running it again

We should able to see invoke transaction and query runs are successful.

![runTest](images/runTest.png)

## Introduction Video
An introduction video is in Videos/bmxJSDKIntro.webm


[![demo video]](https://github.com/IBM-Blockchain/JSDKIntegrationSample/blob/master/Videos/bmxJSDKIntro.webm)



## Video Screen Shots
Screen shots are in screenShots directory