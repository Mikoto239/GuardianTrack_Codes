const Hardware = require('../models/hardware.js');
const MinorAlert = require('../models/minoralerts.js');
const Pinlocation = require('../models/pinlocation.js');
const Theft = require('../models/theftdetails.js');
const TheftAlert = require('../models/theftalert.js');
const User = require('../models/user.js');
const jwt = require('jsonwebtoken');
const SECRET_KEY = process.env.SECRET_KEY;
require('dotenv').config();
const axios = require('axios');


// hardware registration
exports.hardwareregistration = async (req, res, next) => {
  const { uniqueId } = req.body;

  try {
    const existingHardware = await Hardware.findOne({ uniqueId });

    if (existingHardware) {
      const token = jwt.sign({ id: existingHardware._id }, SECRET_KEY);
      return res.status(200).json({
        message: 'Hardware already registered',
        token: token // Send the generated token back to the hardware
      });
    } else {
      const newHardware = new Hardware({ uniqueId });
      await newHardware.save();
      const token = jwt.sign({ id: newHardware._id }, SECRET_KEY); // Use newHardware._id
      return res.status(200).json({
        message: 'Hardware registered successfully',
        token: token
      });
    }
  } catch (error) {
    console.error(error);
    return res.status(500).json({ message: 'Internal server error' });
  }
};



exports.getcurrentpinlocation = async (req, res, next) => {
  const { token } = req.body;

  try {
    const decoded = jwt.verify(token, SECRET_KEY);

    if (!decoded || !decoded.id) { // Check if 'decoded' exists and contains 'id'
      return res.status(401).json({ message: 'Unauthorized Access!' });
    }

    const decodedId = decoded.id; 

    const hardware = await Hardware.findById(decodedId);
    if(!hardware){
      res.status(404).json({message:"Not found!"});
    }

    const pinLocation = await Pinlocation.findOne({ uniqueId:hardware.uniqueId, statusPin: true }).sort({ pinAt: -1 });  
    if (!pinLocation) {
      return res.status(400).json({ message:"No pinned location!"});
    }

    const { currentlatitude: latitude, currentlongitude: longitude, pinAt: time } = pinLocation;

    return res.status(200).json({ latitude, longitude, time });
  } catch (error) {
  
    if (error.name === 'JsonWebTokenError') {
      return res.status(401).json({ message: 'Invalid token' });
    }

    if (error.name === 'TokenExpiredError') {
      return res.status(401).json({ message: 'Token expired' });
    }

    return res.status(500).json({ message: 'Internal server error' });
  }
};



exports.send_theftalert = async (req, res, next) => {
  const { currentlatitude, currentlongitude, token } = req.body;

  try {
    const decoded = jwt.verify(token, SECRET_KEY);

    if (!decoded || !decoded.id) {
      return res.status(401).json({ message: 'Unauthorized Access!' });
    }

    const decodedId = decoded.id; 
    const hardware = await Hardware.findById(decodedId);

    if (!hardware) {
      return res.status(404).json({ message: "Hardware not found!" });
    }
    const response = await axios.get(`https://geocode.maps.co/reverse?lat=${currentlatitude}&lon=${currentlongitude}&api_key=${process.env.OPENCAGE_API_KEY}`);
    const responseData = response.data;

    if (!responseData || !responseData.display_name || !responseData.address) {
      return res.status(404).json({ message: 'No address information found for the provided coordinates' });
    }

    const {
      display_name,
      address: { road, quarter, city, state, region, country_code }
    } = responseData;

    const address = {
      formatted: display_name,
      road,
      quarter,
      city,
      state,
      region,
      country_code
    };

    const newTheftAlert = new TheftAlert({
      currentlatitude,
      currentlongitude,
      uniqueId: hardware.uniqueId,
      address: address.formatted,
      level:"Level 4"
    });

    await newTheftAlert.save();
    res.status(201).json({ message: "Successfully sent!" });

  } catch (error) {
    return res.status(500).json({ message: 'Internal server error' });
  }
};


//send minor alerts
exports.send_alert = async (req, res, next) => {
  const { description, latitude, longitude, level, token } = req.body;
  
  if (!description || !level || !token) {
    return res.status(400).json({ message: 'All fields are required' });
  }

  try {
    const decoded = jwt.verify(token, SECRET_KEY);

    if (!decoded || !decoded.id) {
      return res.status(401).json({ message: 'Unauthorized Access!' });
    }

    const decodedId = decoded.id;
    const hardware = await Hardware.findById(decodedId);

    if (!hardware) {
      return res.status(404).json({ message: 'Hardware not found!' });
    }

    const response = await axios.get(`https://geocode.maps.co/reverse?lat=${latitude}&lon=${longitude}&api_key=${process.env.OPENCAGE_API_KEY}`);
    const responseData = response.data;

    if (!responseData || !responseData.display_name || !responseData.address) {
      return res.status(404).json({ message: 'No address information found for the provided coordinates' });
    }

    const {
      display_name,
      address: { road, quarter, city, state, region, country_code }
    } = responseData;

    const address = {
      formatted: display_name,
      road,
      quarter,
      city,
      state,
      region,
      country_code
    };

    const minoralert = new MinorAlert({
      description,
      latitude,
      longitude,
      uniqueId: hardware.uniqueId,
      level,
      address: address.formatted
    });

    await minoralert.save();
    return res.status(200).json({ message: 'Data saved successfully!' });
  } catch (error) {
    if (error.response) {
      return res.status(error.response.status).json({ message: 'Failed to retrieve address information' });
    } else if (error.name === 'ValidationError') {
      return res.status(400).json({ message: 'Validation error', details: error.errors });
    } else {
      return res.status(500).json({ message: 'Failed to save data', error: error.message });
    }
  }
};




exports.pinlocation = async (req, res, next) => {
  const { pinlocation, currentlatitude, currentlongitude, statusPin, token, status } = req.body;

  if (!token) {
    return res.status(401).json({ message: 'No token provided' });
  }

  try {
    const decoded = jwt.verify(token, SECRET_KEY);

    if (!decoded || !decoded.id) {
      return res.status(401).json({ message: 'Unauthorized Access!' });
    }

    const decodedId = decoded.id;
    const hardware = await Hardware.findOne({ _id: decodedId });

    if (!hardware) {
      return res.status(404).json({ message: 'Hardware not found!' });
    }

    if (!currentlatitude || !currentlongitude) {
      return res.status(400).json({ message: 'Invalid location coordinates' });
    }

    const hardwareuniqueid = hardware.uniqueId;
    const existingPinLocations = await Pinlocation.find({ uniqueId: hardwareuniqueid, statusPin: true });

    if (existingPinLocations.length > 0) {
      await Hardware.findOneAndUpdate(
        { uniqueId: hardwareuniqueid },
        { pinlocation: false},
        { new: true }
      );
      return res.status(400).json({ message: "Error fetching the location" });
    }

    const response = await axios.get(`https://geocode.maps.co/reverse?lat=${currentlatitude}&lon=${currentlongitude}&api_key=${process.env.OPENCAGE_API_KEY}`);
    const responseData = response.data;

    if (!responseData || !responseData.display_name || !responseData.address) {
      return res.status(404).json({ message: 'No address information found for the provided coordinates' });
    }

    const { display_name, address: { road, quarter, city, state, region, country_code } } = responseData;

    const address = {
      formatted: display_name,
      road,
      quarter,
      city,
      state,
      region,
      country_code
    };

    // Find the pin location with statusPin: true and uniqueId
    const existingPinLocation = await Pinlocation.findOne({ uniqueId: hardwareuniqueid, statusPin: true }).sort({ pinAt: -1 });

    if (existingPinLocation) {
      existingPinLocation.statusPin = false;
      await existingPinLocation.save();
    }

    const updatedHardware = await Hardware.findOneAndUpdate(
      { _id: decodedId },
      { pinlocation: false, status: true },
      { new: true }
    );

    if (!updatedHardware) {
      return res.status(404).json({ message: 'Hardware not found' });
    }

    // Create a new pin location document with the updated status
    const pinLocationSave = new Pinlocation({
      uniqueId: hardwareuniqueid,
      currentlatitude,
      currentlongitude,
      address: address.formatted,
      statusPin: true
    });

    await pinLocationSave.save();

    return res.status(200).json({
      message: 'Location updated successfully',
      latitude: currentlatitude,
      longitude: currentlongitude,
      address: address.formatted
    });

  } catch (error) {
    console.error('Error updating hardware:', error);
    if (error.name === 'JsonWebTokenError') {
      return res.status(401).json({ message: 'Invalid token' });
    }
    return res.status(500).json({
      message: 'Internal server error',
      error: error.message
    });
  }
};


exports.pinstatus = async (req, res, next) => {
  const { token } = req.body;

  try {
   const decoded = jwt.verify(token, SECRET_KEY);

    if (!decoded || !decoded.id) {
      return res.status(401).json({ message: 'Unauthorized Access!' });
    }

    const decodedId = decoded.id;
    const hardware = await Hardware.findOne({ _id: decodedId });
    if (!hardware) {
      return res.status(404).json({ message: 'Hardware not found!' });
    }
    const pinStatus = hardware.pinlocation;

    return res.status(200).json({ status: pinStatus });
  } catch (error) {
    console.error(error);
    return res.status(500).json({ message: 'Internal server error' });
  }
};

exports.theftdetails = async (req, res, next) => {
  const { token, currentlatitude, currentlongitude, description, level } = req.body;

  try {
    const decoded = jwt.verify(token, SECRET_KEY);

    if (!decoded || !decoded.id) {
      return res.status(401).json({ message: 'Unauthorized Access!' });
    }

    const decodedId = decoded.id;
    const hardware = await Hardware.findOne({ _id: decodedId });

    if (!hardware) {
      return res.status(404).json({ message: 'Hardware not found!' });
    }

    const response = await axios.get(`https://geocode.maps.co/reverse?lat=${currentlatitude}&lon=${currentlongitude}&api_key=${process.env.OPENCAGE_API_KEY}`);

    const responseData = response.data;

    if (!responseData || !responseData.display_name || !responseData.address) {
      return res.status(404).json({ message: 'No address information found for the provided coordinates' });
    }

    const {
      display_name,
      address: { road, quarter, city, state, region, country_code }
    } = responseData;

    const address = {
      formatted: display_name,
      road,
      quarter,
      city,
      state,
      region,
      country_code
    };

    const theftDetail = new Theft({
      uniqueId: hardware.uniqueId,
      currentlatitude,
      currentlongitude,
      description,
      level,
      address: address.formatted
    });

    await theftDetail.save();

    return res.status(200).json({ message: 'Theft details saved successfully' });
  } catch (error) {
    console.error('Error saving theft details:', error);
    return res.status(500).json({ message: 'Internal server error' });
  }
};



exports.getusernumber = async (req, res, next) => {
  const { token } = req.body;
    
  try {
    const decoded = jwt.verify(token, SECRET_KEY);

    if (!decoded || !decoded.id) {
      return res.status(401).json({ message: 'Unauthorized Access!' });
    }

    const decodedId = decoded.id;
    const hardware = await Hardware.findOne({ _id: decodedId });

    if (!hardware) {
      return res.status(404).json({ message: 'Hardware not found!' });
    }
    const user = await User.findOne({ uniqueId:hardware.uniqueId});
    if (!user) {
      return res.status(404).json({ message: 'User not found' });
    } 

    const userNumber = user.cellphonenumber;
    return res.status(200).json({ cellphonenumber: userNumber });
  } catch (error) {
    console.error(error);
    return res.status(500).json({ message: 'Internal server error' });
  }
};
