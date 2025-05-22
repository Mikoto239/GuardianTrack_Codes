const express = require('express');
const router = express.Router();
const {offrequest,getlocation,turnoffhardware,turnOnhardware,userexist,deletetheft,theftalert, userregistration, deleteuser, changestatus, latestnotification,allnotification,pinhistory,send_theftalert,updateusernumber,sendtoken,mapnotification} = require('../controllers/software');
const {authenticateToken} = require('../middleware/authorization');
const { isHardwareOn } = require('../middleware/mobilehardwarestatus');
router.post('/userregister',userregistration);
router.post('/getlocation',authenticateToken,isHardwareOn,getlocation);
router.post('/turnoff',authenticateToken,turnoffhardware);
router.post('/currentlocation',authenticateToken,turnOnhardware);
router.post('/checkuser',authenticateToken,userexist);
router.post('/removetheftdetails',authenticateToken,deletetheft);
router.post('/theftalert',authenticateToken,isHardwareOn ,theftalert);
router.post('/deleteuser',authenticateToken,deleteuser);
router.post('/changestatus',authenticateToken,changestatus);
router.post('/allnotification',authenticateToken,allnotification);
router.post('/getlatestnotification',authenticateToken,isHardwareOn ,latestnotification);
router.post('/getpinhistory',authenticateToken,pinhistory);
router.post('/currenttheftlocation',authenticateToken,isHardwareOn ,send_theftalert );
router.post('/updateusernumber',authenticateToken,updateusernumber);
router.post('/sendtoken',sendtoken);
router.post('/latestmapnotification',authenticateToken,isHardwareOn,mapnotification);
router.post('/offrequest',authenticateToken,offrequest);
module.exports = router;


