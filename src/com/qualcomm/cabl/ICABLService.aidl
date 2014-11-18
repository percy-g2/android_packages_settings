package com.qualcomm.cabl;

interface ICABLService{

 boolean startCABL() ;
 boolean stopCABL() ;
 boolean setCABLLevel(int level);
 void stopListener();
 void setCABLStateOnResume(boolean cablStatus);
}