package com.teleonome.fertilizer;

public class FertilizationIdentity {

	public String identifier, moduleName="", homeBoxName="", homeBoxDeneName="";
	 
	public FertilizationIdentity(String denePointer){
		denePointer = denePointer.substring(1);
		String[] tokens = denePointer.split(":");
		//  
		// at this point we have something like
		// Sperm:Pacemaker:General Status LED:Identity LED
		// note tha tthe last token will not appear in actions related denes 
		 
		identifier= tokens[0];
		moduleName= tokens[1];
		homeBoxName= tokens[2];
		if(tokens.length>3) {
			homeBoxDeneName= tokens[3];
		}
	}
	
	public String getHomeoBoxName(){
		return homeBoxName;
	}
	
	public String getHomeBoxDeneName(){
		return homeBoxDeneName;
	}
	
}
