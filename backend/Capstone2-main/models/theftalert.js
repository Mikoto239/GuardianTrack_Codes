const mongoose = require('mongoose');
const moment = require('moment-timezone');

const theft_alert = new mongoose.Schema({
  currentlatitude: Number,
  currentlongitude: Number,
  uniqueId: String,
  level:String,
  address:String,
  happenedAt: {
    type: Date,
 default: () => moment.tz('Asia/Manila').add(8, 'hours').toDate() 
  }
});

const theftalert = mongoose.model('TheftAlert', theft_alert);

module.exports = theftalert;