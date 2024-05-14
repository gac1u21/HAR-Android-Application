from flask import Flask, request
import pickle
import os
import uuid
from aeon.datasets import load_from_tsfile

app = Flask(__name__)

file_path = os.path.join(os.path.dirname(__file__), 'rocket_classifier_model.pkl') #Path to the wrapped classifier (.pkl file)

#Initialise the classifier
classifier = None
with open(file_path, 'rb') as f:
    classifier = pickle.load(f)

@app.route('/')
def home():
    """Displays a welcome message."""
    return 'Welcome to the HAR Server!'

@app.route('/predict', methods=['POST'])
def predict():
    """Runs the classifier on the received data"""

    #Save the received data in a .ts file
    unique_filename = f"{uuid.uuid4()}.ts"
    data = request.data.decode('utf-8')
    path = os.path.join(os.path.dirname(__file__), unique_filename)
    with open(path, 'w') as file:
        file.write(data)

    validate_series_length(path)
    
    #Load the file
    X_test, y_test = load_from_tsfile(path)

    #Run the classifier on it
    pred = classifier.predict(X_test)
    print("Prediction: ",pred)
    #os.remove(file_path)

    return f"You are performing {pred[0]}"

@app.route('/upload_labeled_activity', methods=['POST'])
def upload_labeled_activity():
    """Trains the classifier with the received data"""

    #Load the received data
    global classifier
    unique_filename = f"{uuid.uuid4()}.ts"
    data = request.data.decode('utf-8')

    #Get the label of the activity
    *sensor_data, label = data.rsplit(':', 1)
    sensor_data = ':'.join(sensor_data)

    #Save the data in a file
    file_path = os.path.join(os.path.dirname(__file__), f'{unique_filename}')
    with open(file_path, 'w') as file:
        file.write(data)

    #Add the data to the classifier's training dataset
    train_path = os.path.join(os.path.dirname(__file__), "TRAIN3 - 50hz.ts")
    with open(train_path, 'a') as file:
        file.write(f"\n{data}")
        
    #Add the label on the 8th line of the training dataset (where all activity labels are)
    append_text_to_line(train_path, 8, label.strip())

    #Get the content of the file that trains the classifier
    file_path = os.path.join(os.path.dirname(__file__), "classifier-train.py")
    with open(file_path, 'r') as file:
        script_content = file.read()

    exec(script_content) #Run the returned content as python code, thus re-training the classifier

    #Load the newly trained classifier
    file_path = os.path.join(os.path.dirname(__file__), 'rocket_classifier_model.pkl')
    with open(file_path, 'rb') as f:
        classifier = pickle.load(f)

    return f"Data for {label.strip()} was received and saved"

def append_text_to_line(file_path, line_number, text):
    """Append labelled data to training dataset"""

    #Load file
    with open(file_path, 'r') as file:
        lines = file.readlines()
    
    #Add text to line if it is not already there
    if line_number <= len(lines):
        if text not in lines[line_number - 1]:
            lines[line_number - 1] = lines[line_number - 1].rstrip('\n') + ' ' + text + '\n'
    
    #Save file
    with open(file_path, 'w') as file:
        file.writelines(lines)

def validate_series_length(file_path):
    """Check if all 6 channels in the file have exactly 500 values."""

    #Load file
    with open(file_path, 'r') as file:
        data = file.read()

    #Splits the data into 6 channels
    channels_data = data.split(':')
    first_6_channels_data = channels_data[:6]

    #Returns the lengths of the first 6 channels and prints them
    lengths = [len(channel.split(',')) for channel in first_6_channels_data]
    print("Lengths of channels: ",lengths)
    
    return all(length == 100 for length in lengths)

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)