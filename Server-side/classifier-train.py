import pickle
from aeon.classification.convolution_based import RocketClassifier
from aeon.datasets import load_from_tsfile

#Load the training data from the .ts file
X_train, y_train = load_from_tsfile("path_to/TRAIN3 - 50hz.ts")

#Initialise and train the classifier
clf = RocketClassifier()
clf.fit(X_train, y_train)

#Wrap the trained classifier in a .pkl file
with open('path_to/rocket_classifier_model.pkl', 'wb') as f:
    pickle.dump(clf, f)