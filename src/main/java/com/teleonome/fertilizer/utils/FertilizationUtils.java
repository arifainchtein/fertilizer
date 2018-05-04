package com.teleonome.fertilizer.utils;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.teleonome.fertilizer.FertilizationIdentity;
import com.teleonome.framework.TeleonomeConstants;
import com.teleonome.framework.denome.DenomeUtils;
import com.teleonome.framework.denome.Identity;
import com.teleonome.framework.exception.InvalidDenomeException;
import com.teleonome.framework.utils.Utils;

public class FertilizationUtils {
	static String lineSep = System.lineSeparator(); 
	
	public static JSONObject getDeneBySpermIdentity(JSONObject completeSpermJSONObject, FertilizationIdentity fertilizationIdentity) throws JSONException{
		//
		// get the name of the new teleonome
		JSONObject spermJSONObject = completeSpermJSONObject.getJSONObject(TeleonomeConstants.SPERM);
		JSONObject pacemakerJSONObject = spermJSONObject.getJSONObject(TeleonomeConstants.SPERM_HYPOTHALAMUS);
		JSONArray homeboxesJSONArray = pacemakerJSONObject.getJSONArray(TeleonomeConstants.SPERM_HYPOTHALAMUS_HOMEOBOXES);
		JSONArray actionsJSONArray = pacemakerJSONObject.getJSONArray(TeleonomeConstants.SPERM_HYPOTHALAMUS_ACTIONS);
		
		JSONObject homeoboxJSONObject,actionJSONObject;
		JSONArray homeoboxDenes, actionDenes;
		JSONObject homeoboxDene,actionDene;
		//
		// first the actions
		for(int i=0;i<actionsJSONArray.length();i++){
			actionJSONObject = actionsJSONArray.getJSONObject(i);
			if(actionJSONObject.getString(TeleonomeConstants.DENEWORD_NAME_ATTRIBUTE).equals(fertilizationIdentity.getHomeoBoxName())){
				return actionJSONObject;
			}
		}
		//
		// if we are still here check the homeboxes
		//
		for(int i=0;i<homeboxesJSONArray.length();i++){
			homeoboxJSONObject = homeboxesJSONArray.getJSONObject(i);
			if(homeoboxJSONObject.getString(TeleonomeConstants.DENEWORD_NAME_ATTRIBUTE).equals(fertilizationIdentity.getHomeoBoxName())){
				homeoboxDenes = homeoboxJSONObject.getJSONArray(TeleonomeConstants.SPERM_HOMEOBOX_DENES);
				for(int j=0;j<homeoboxDenes.length();j++){
					homeoboxDene = homeoboxDenes.getJSONObject(j);
					if(homeoboxDene.getString(TeleonomeConstants.DENEWORD_NAME_ATTRIBUTE).equals(fertilizationIdentity.getHomeBoxDeneName())){
						return homeoboxDene;
					}
				}
				
			}
		}
		
		return null;
	}
	
	public static String verifySperm(JSONObject eggJSONObject, JSONObject completeSpermJSONObject) throws JSONException{
		JSONObject spermJSONObject = completeSpermJSONObject.getJSONObject(TeleonomeConstants.SPERM);
		StringBuffer errorsBuffer = new StringBuffer();
		JSONObject pacemakerJSONObject = spermJSONObject.getJSONObject(TeleonomeConstants.SPERM_HYPOTHALAMUS);
		JSONArray homeboxesJSONArray = pacemakerJSONObject.getJSONArray(TeleonomeConstants.SPERM_HYPOTHALAMUS_HOMEOBOXES);
		JSONObject homeoboxJSONObject;
		JSONArray homeoboxDenes;
		JSONObject homeoboxDene;
		ArrayList allDenePointersForHomebox;
		String denePointer;
		FertilizationIdentity fertilizationIdentity;
		JSONObject hoxDene;
		String hoxDeneTargetPointer;
		Identity hoxDeneTargetIdentity;
		JSONArray homeoboxIndexDeneWords;
		JSONObject homeoboxIndexDeneWord, resolvedDene;
		String hoxDenePointerValue;
		boolean foundHomeoBoxIndex=false;
		boolean foundAtLeastOneHoxDenePointer=false;
		for(int i=0;i<homeboxesJSONArray.length();i++){
			homeoboxJSONObject = homeboxesJSONArray.getJSONObject(i);
			foundHomeoBoxIndex=false;
			allDenePointersForHomebox = new ArrayList();
			homeoboxDenes = homeoboxJSONObject.getJSONArray(TeleonomeConstants.SPERM_HOMEOBOX_DENES);
			for(int j=0;j<homeoboxDenes.length();j++){
				homeoboxDene = homeoboxDenes.getJSONObject(j);
				if(homeoboxDene.getString(TeleonomeConstants.DENEWORD_NAME_ATTRIBUTE).equals(TeleonomeConstants.SPERM_HOMEOBOX_INDEX)){
					foundHomeoBoxIndex=true;
					//
					// we are in the correct dene to get the index of all the other denes that need to be acted upon in this homebox
					// get the deneword of type Hox Dene Pointer
					homeoboxIndexDeneWords = homeoboxDene.getJSONArray("DeneWords");
					foundAtLeastOneHoxDenePointer=false;
					for(int k=0;k<homeoboxIndexDeneWords.length();k++){
						homeoboxIndexDeneWord = homeoboxIndexDeneWords.getJSONObject(k);
						System.out.println("homeoboxIndexDeneWord=" + homeoboxIndexDeneWord.toString(4));
						if(homeoboxIndexDeneWord.getString(TeleonomeConstants.DENEWORD_DENEWORD_TYPE_ATTRIBUTE).equals(TeleonomeConstants.SPERM_HOX_DENE_POINTER)){
							foundAtLeastOneHoxDenePointer=true;
							denePointer =  homeoboxIndexDeneWord.getString(TeleonomeConstants.DENEWORD_VALUE_ATTRIBUTE);
							fertilizationIdentity = new FertilizationIdentity(denePointer);
							//
							//
							hoxDene = FertilizationUtils.getDeneBySpermIdentity( completeSpermJSONObject,  fertilizationIdentity) ;
							if(hoxDene==null){
								errorsBuffer.append("In the Homeobox Index, of the Homeobox named " + homeoboxJSONObject.getString(TeleonomeConstants.DENEWORD_NAME_ATTRIBUTE) + " there is an invalid pointer, pointing to " + denePointer + lineSep);
								
							}else{
								if(hoxDene.has(TeleonomeConstants.SPERM_HOX_DENE_TARGET)) {
									hoxDeneTargetPointer = hoxDene.getString(TeleonomeConstants.SPERM_HOX_DENE_TARGET);
									hoxDeneTargetIdentity = new Identity(hoxDeneTargetPointer);	
									resolvedDene = DenomeUtils.getDenomicElementByIdentity(eggJSONObject, hoxDeneTargetIdentity);
									
									if(resolvedDene==null){
										errorsBuffer.append("In the Homeobox Index, of the Homeobox named " + homeoboxJSONObject.getString(TeleonomeConstants.DENEWORD_NAME_ATTRIBUTE) + " the Hox Dene named " + hoxDene.getString(TeleonomeConstants.DENEWORD_NAME_ATTRIBUTE) + " has invalid target, pointing to " + hoxDeneTargetPointer  + lineSep);
									}
								}else {
									errorsBuffer.append("In the Homeobox Index, of the Homeobox named " + homeoboxJSONObject.getString(TeleonomeConstants.DENEWORD_NAME_ATTRIBUTE) + " the Hox Dene named " + hoxDene.getString(TeleonomeConstants.DENEWORD_NAME_ATTRIBUTE) + " is missing the target"   + lineSep);
									
								}
								
							}
						}
						
					}
					if(!foundAtLeastOneHoxDenePointer){
						errorsBuffer.append("The Homeobox named " + homeoboxDene.getString(TeleonomeConstants.DENEWORD_NAME_ATTRIBUTE) + " must have at least one deneword of type " + TeleonomeConstants.SPERM_HOX_DENE_POINTER + lineSep);
					}
				}else {
					//
					// is not a homeobox index, now check to see if
				}
				
			}
			//
			// if this homebox does not have a dene of type SPERM_HOMEOBOX_INDEX throw an error
			//
			if(!foundHomeoBoxIndex){
				errorsBuffer.append("The Homeobox named " + homeoboxJSONObject.getString(TeleonomeConstants.DENEWORD_NAME_ATTRIBUTE) + " is missing a Dene of type " + TeleonomeConstants.SPERM_HOMEOBOX_INDEX + lineSep);
			}
			//
			// check if any of the microcontrollers in the sperm have repeated ProcessingQueue Position
			
//			duplicates=false;
//			for (j=0;j<zipcodeList.length;j++)
//			  for (k=j+1;k<zipcodeList.length;k++)
//			    if (k!=j && zipcodeList[k] == zipcodeList[j])
//			      duplicates=true;
//			
		}
		if(errorsBuffer.toString().equals("")){
			return TeleonomeConstants.SPERM_VALIDATED;
		}else{
			return errorsBuffer.toString();
		}
		
	}
}
