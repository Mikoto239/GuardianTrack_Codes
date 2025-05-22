const User = require('../models/user');
const Hardware = require('../models/hardware');
const jwt = require('jsonwebtoken');
const SECRET_KEY = process.env.SECRET_KEY;

exports.isHardwareOn = async (req, res, next) => {
  const { token } = req.body;
  if (!token) {
    return res.status(401).json({ message: 'Unauthorized Access! Token is missing.' });
  }

  try {
    
    const decoded = jwt.verify(token, SECRET_KEY);

    if (!decoded || !decoded.id) {
      return res.status(401).json({ message: 'Unauthorized Access!' });
    }

    const decodedId = decoded.id;
   
    const user = await User.findOne({ _id: decodedId });
    const uniqueId = user.uniqueId;
    const hardware = await Hardware.findOne({uniqueId});
    
    if ( !hardware.status) {
      return res.status(404).json({ message: 'Hardware is Off!' });
    }

 
    next();
  } catch (error) {
    console.error("Error:", error);
    return res.status(500).json({ message: "Internal Server Error!" });
  }
};
