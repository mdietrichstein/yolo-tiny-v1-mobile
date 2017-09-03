
# Yolo for Android and iOS - Mobile Deep Learning Object Detection in Realtime

This repository contains an implementation of the (Tiny) Yolo V1 objector detector for both Android and iOS.

## Yolo?

This Ted Talk by the creator of Yolo itself gives a nice high-level overview: [Joseph Redmon - How a computer learns to recognize objects instantly](https://www.ted.com/talks/joseph_redmon_how_a_computer_learns_to_recognize_objects_instantly).

You should also check out his [CV](https://pjreddie.com/static/Redmon%20Resume.pdf). Really, do it ;)

The notebooks provided in this repository contain some more references regarding Yolo.


## Motivation

This project came to be because I wanted to apply the knowledge I have gained from various Deep Learning related courses over the year in a practical project and was searching for a workflow which supports:

* Model Exploration/Implementation
* Model Training/Validation
* Model Optimization
* Deployment on iOS and Android

## Features

* Realtime object detection
* Support for Android and iOS
* "Live" Switching between Portrait and Landscape Orientation

## Prerequisites

### Jupyter Notebooks

The notebooks should be compatible with Python 3.5, Keras 2, Tensorflow 1.2.x. You can find the complete list of dependencies in `environment.yml`

### Android

The Android app is written in Kotlin and should work with any Android Studio Version from 3.x onwards.

### iOS

Run `pod install` to install the required dependencies via Cocoapods. The iOS app is written in Swift 3 and Object C++ and should work with a recent version of Xcode.


## Build Process

### 0. Create `notebooks/tf-exports` folder

The notebooks will use this folder to export models to.

### 1. Follow the instructions in `notebooks/01_exploration.ipynb` to create a keras model with tensorflow backend

This notebook documents the process of implementing Yolo in Keras, converting the pretrained darknet weights for keras and converting them to a format compatible with the tensorflow backend.

### 2. Follow the instructions in `notebooks/02_export_to_tf.ipynb` to export an optimized tensorflow model

This notebook shows how to export the keras model to tensorflow and how to optimize ot for inference. The resulting `frozen_yolo.pb` file contains the tensorflow model that will be loaded by the mobile apps.

### 3. Include `frozen_yolo.pb` in mobile projects

_iOS_: Open the project in XCode and drag and drop `frozen_yolo.pb` into XCode.

_Android_: Create a folder named ` mobile/Android/YoloTinyV1Tensorflow/app/src/main/assets` and copy `frozen_yolo.pb` into it.


## Improvements

### Overlapping Detection Boxes
The mobile apps do not use Non-Maximum Suppression yet. This means that the apps will display multiple boxes for the same object. I will add this feature to the apps soon. Check out `notebooks/01_exploration.ipynb` if you're interested in how this works, or you want to implement it youself.

### Performance
Performance on Android and iOS is suboptimal. There are some opportunities to improve performance (e.g. weight quantization). Will definitely look into this some more.

### Camera Switching
Both apps only use the back camera. A camera switcher would be a nice improvement.

