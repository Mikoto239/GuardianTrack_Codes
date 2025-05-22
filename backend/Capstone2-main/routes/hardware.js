
const express = require('express');
const router = express.Router();
const { getcurrentpinlocation, send_alert, pinlocation, theftdetails, getusernumber, hardwareregistration, pinstatus ,send_theftalert} = require('../controllers/hardware.js');
const { isHardwareOn } = require('../middleware/hardwarestatus');
const {authenticateToken} = require('../middleware/authorization');

router.post('/checkpinlocation',authenticateToken, isHardwareOn,getcurrentpinlocation);
router.post('/send_alert',authenticateToken,isHardwareOn,send_alert);
router.post('/pinthislocation',authenticateToken,pinlocation);
router.post('/pinstatus',authenticateToken,pinstatus);
router.post('/sendtheftdetails',authenticateToken,isHardwareOn,theftdetails);
router.post('/hardwareregister',hardwareregistration);
router.post('/usernumber',authenticateToken,isHardwareOn, getusernumber);
router.post('/currentvehiclelocation',authenticateToken,isHardwareOn,send_theftalert);

module.exports = router;
