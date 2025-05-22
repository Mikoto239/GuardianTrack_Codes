const jwt = require('jsonwebtoken'); // Ensure you have the jwt package required
const jwt_key = process.env.SECRET_KEY; // Make sure your JWT key is stored securely

exports.authenticateToken = async (req, res, next) => {
  const authHeader = req.header('Authorization');
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ message: 'Access denied, no token provided' });
  }

  const token = authHeader.replace('Bearer ', '').trim();

  console.log('Token:', token);

  try {
    const decoded = jwt.verify(token, jwt_key);
    console.log('Decoded Payload:', decoded);
    req.user = decoded;
    next();
  } catch (error) {
    console.error('Token verification error:', error);
    res.status(400).json({ message: token, error: error.message });
  }
};
