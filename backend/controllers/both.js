const Hardware = require('../models/hardware');
const User = require('../models/user.js');
const jwt = require('jsonwebtoken');
require('dotenv').config();
const SECRET_KEY = process.env.SECRET_KEY;

// Check hardware status
exports.mobilehardwarestatus = async (req, res, next) => {
  const { token } = req.body;

  try {
    const decoded = jwt.verify(token, SECRET_KEY);

    if (!decoded || !decoded.id) {
      return res.status(401).json({ message: 'Unauthorized Access!' });
    }

    const decodedId = decoded.id;
    const userexist = await User.findById(decodedId);
    if (!userexist) {
      return res.status(404).json({ message: 'User not found!' });
    }

    const uniqueId = userexist.uniqueId;
    const hardware = await Hardware.findOne({ uniqueId });

    if (!hardware) {
      return res.status(404).json({ message: 'Hardware not found' });
    }

    const hardwareStatus = hardware.status;

    return res.status(200).json({ status: hardwareStatus });
  } catch (error) {
    console.error(error);
    return res.status(500).json({ message: 'Internal server error' });
  }
};


exports.hardwarestatus = async (req, res, next) => {
  const { token } = req.body;

  try {

    const decoded = jwt.verify(token, SECRET_KEY);

    if (!decoded || !decoded.id) {
      return res.status(401).json({ message: 'Unauthorized Access!' });
    }

    const decodedId = decoded.id;

 
    const hardwareexist = await Hardware.findById(decodedId);
    if (!hardwareexist) {
      return res.status(404).json({ message: 'Hardware not found!' });
    }

    
    const uniqueId = hardwareexist.uniqueId;

  
    const hardware = await Hardware.findOne({ uniqueId });
    if (!hardware) {
      return res.status(404).json({ message: 'Hardware not found!' });
    }

 
    const hardwareStatus = hardware.status;

    return res.status(200).json({ status: hardwareStatus });
  } catch (error) {
    console.error(error);
    return res.status(500).json({ message: 'Internal server error' });
  }
};
