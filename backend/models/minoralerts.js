
const mongoose = require('mongoose');
const moment = require('moment-timezone'); // Import the moment module

moment.tz.setDefault('Asia/Manila'); // Set default timezone

const minorAlertDataSchema = new mongoose.Schema({
  description: { type: String, required: true }, // Changed 'Description' to 'description' for consistency
  level: { type: String, required: true },
  latitude: { type: Number },
  longitude: { type: Number},
  uniqueId: { type: String, required: true },
  address:{ type: String, required: true },
  vibrateAt: {
    type: Date,
    default: () => moment.tz('Asia/Manila').add(8, 'hours').toDate() 
  }
});

// Correctly name the model to reflect its purpose
const MinorAlert = mongoose.model('MinorAlert', minorAlertDataSchema);

module.exports = MinorAlert;
