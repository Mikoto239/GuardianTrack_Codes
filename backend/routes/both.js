const express = require('express');
const router = express.Router();
const {mobilehardwarestatus, hardwarestatus} = require('../controllers/both.js');
const { isHardwareOn } = require('../middleware/hardwarestatus');
const {authenticateToken} = require('../middleware/authorization');

router.post('/hardwarestatus',authenticateToken,hardwarestatus);
router.post('/mobilehardwarestatus',authenticateToken,mobilehardwarestatus);

module.exports = router;
