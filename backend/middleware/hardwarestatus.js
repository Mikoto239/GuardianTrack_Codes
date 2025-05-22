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
   
    const hardware = await Hardware.findOne({ _id: decodedId });

    if (!hardware || !hardware.status) {
      return res.status(404).json({ message: 'Hardware is Off!'+hardware });
    }

 
    next();
  } catch (error) {
    console.error("Error:", error);
    return res.status(500).json({ message: "Internal Server Error!" });
  }
};
