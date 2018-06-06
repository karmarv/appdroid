# ARCore Cloud Anchors Sample

This is a sample app demonstrating how ARCore anchors can be hosted and resolved
using the ARCore Cloud Service.

## Getting Started

 See [Get started with Cloud Anchors for Android](https://developers.google.com/ar/develop/java/cloud-anchors/cloud-anchors-quickstart-android)
 to learn how to set up your development environment and try out this sample app.

 
 

## Google Cloud Platform  for hosting an Anchor
###### The anchor's pose and limited data about the user's physical surroundings is uploaded to the ARCore Cloud Anchor Service. Once an anchor is hosted successfully, the anchor becomes a Cloud Anchor and is assigned a unique cloud anchor ID.
- For the ARCore cloud anchor service: https://console.cloud.google.com/apis/api/arcorecloudanchor.googleapis.com/overview?project=cs281-197703&folder&organizationId&duration=PT1H
- Firebase for storing the room Id/notifications https://console.firebase.google.com/u/0/project/huntar-88a42/database/huntar-88a42/data


## Reference
- cloud-anchor-java projects in https://github.com/google-ar/arcore-android-sdk
###### Ref
- https://www.codementor.io/flame3/send-push-notifications-to-android-with-firebase-du10860kb
- https://firebase.google.com/docs/cloud-messaging/android/client
- https://codelabs.developers.google.com/codelabs/firebase-android/#5
- https://github.com/firebase/quickstart-android/blob/master/messaging/app/src/main/java/com/google/firebase/quickstart/fcm/MainActivity.java

###### Send cloud messages from android on a topic 
- https://androidredman.wordpress.com/2017/08/27/send-push-notifications-from-1-android-phone-to-another-with-out-server/ 

https://codelabs.developers.google.com/codelabs/arcore-cloud-anchors/#2
https://github.com/dat-ng/ar-location-based-android


Simulators: 
https://developers.google.com/ar/develop/java/emulator