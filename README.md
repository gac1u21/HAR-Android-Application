# Human Activity Recognition Android App

**Overview**
This repository contains the files for a Human Activity Recognition Android App, which uses sensor data from the mobile device to detect the user's activity in real time. The app interfaces with a Python Flask server that processes the sensor data using a machine learning model, classifies the activity, and sends the result back to the app.

The repository is structured into two main parts:
**Android App** - Contains the source code for the Android application.
**Server-side** - Contains the Python server code, the training dataset, and the scripts for training the machine learning model.

**Server-side Details**
Inside the "Server-side" folder, you will find:

**classifier-train.py**: A Python script for training the Rocket Classifier from the aeon package with time series data.
**server.py**: The Flask server script that processes data received from the Android app and sends back the classification results.
**TRAIN3 - 50hz.ts**: The training dataset file in .ts format, compatible with the Rocket Classifier.
**knn_classifier_model.pkl**: The pre-trained machine learning model file.

**Getting Started**
Prerequisites:
Python 3.x
Flask
aeon package (Install using pip install aeon)
Android Studio for deploying the app

**Training the Classifier**
Before deploying the server, the classifier must be trained with the available time series data:
1. Navigate to the "Server-side" folder.
2. Run the following command to train the classifier: python classifier-train.py
This script will train the Rocket Classifier using data from TRAIN3 - 50hz.ts and save the trained model in knn_classifier_model.pkl.

**Deploying the Server**
After training the classifier:
1. Start the Flask server by running: python server.py
This will deploy the server locally and it will listen for HTTP requests from the Android app.

**Setting Up the Android App**
1. Open Android Studio and import the Android app project.
2. Before running the app, update the IP address in mainactivity.kt to the one where the Python server is running.
3. Deploy the app on your Android device using Android Studio's built-in tools.

**Usage**
Once the app is installed and the server is running, open the app on your Android device to access the following functionalities:

1. **Start Recording**: Tap this button to begin a single 10-second recording of your current activity. After recording, the data is sent to the server, where the activity is predicted and the result is displayed on the app.

2. **Start Continuous Recording**: This button initiates continuous 10-second recordings. After each recording interval, the data is sent to the server for activity prediction, and results are displayed sequentially in the app. This allows for ongoing monitoring and activity recognition.

3. **Start Labelled Recording**: Use this option to perform a 10-second recording while manually labeling the activity. After recording, you will be prompted to enter the name of the activity. This new data is then used to further train and refine the classifier on the server, enhancing its accuracy and adaptability.

4. **Show Records**: Selecting this button displays a log of all recorded activities, including what actions were performed and the specific dates and times they occurred. This feature is useful for reviewing and analyzing activity patterns over time.
