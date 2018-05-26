# ARCore Cloud Anchors Sample

This is a sample app demonstrating how ARCore anchors can be hosted and resolved
using the ARCore Cloud Service.

## Getting Started

 See [Get started with Cloud Anchors for Android](https://developers.google.com/ar/develop/java/cloud-anchors/cloud-anchors-quickstart-android)
 to learn how to set up your development environment and try out this sample app.

 
 

## Google Cloud Platform  for hosting an Anchor
###### The anchor's pose and limited data about the user's physical surroundings is uploaded to the ARCore Cloud Anchor Service. Once an anchor is hosted successfully, the anchor becomes a Cloud Anchor and is assigned a unique cloud anchor ID.
- For the ARCore cloud anchor service: https://console.cloud.google.com/apis/api/arcorecloudanchor.googleapis.com/overview?project=cs281-197703&folder&organizationId&duration=PT1H

##### Firebase for storing the room Id & notifications
https://console.firebase.google.com/u/0/project/huntar-88a42/database/huntar-88a42/data


## Reference
- cloud-anchor-java projects in https://github.com/google-ar/arcore-android-sdk


https://codelabs.developers.google.com/codelabs/arcore-cloud-anchors/#2
https://github.com/dat-ng/ar-location-based-android


Simulators: 
https://developers.google.com/ar/develop/java/emulator