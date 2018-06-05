
package com.teleonome.fertilizer.utils;

import com.teleonome.framework.TeleonomeConstants;
import com.teleonome.framework.utils.Utils;
import java.io.*;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONObject;

public class GenerateSensorHomeoboxFromCSV
{
	String fileName;
    String homeboxName;
    String sensorName;
    String teleonomeName;
    String microControllerName;
    private String buildNumber="15/05/2017 18:47";
    Logger logger;
    
    public GenerateSensorHomeoboxFromCSV()
    {
    	String FILE_SEP = System.getProperty("file.separator");
        String fileName =  Utils.getLocalDirectory()  + "Log4J.properties";
        System.out.println("Reading log4j properties " +fileName);								
        PropertyConfigurator.configure(fileName);		
		
		logger = Logger.getLogger(getClass());
		logger.warn("WeatherDataMover Build " + buildNumber);
        
        fileName = "/Users/arifainchtein/Data/Teleonome/Tlaloc/SensorValueDescription.csv";
        homeboxName = "Tlaloc Data CVS Sensor";
        sensorName = "Tlaloc Data CVS Sensor";
        teleonomeName = "Tlaloc";  
        microControllerName = "CSV MicroController";
        try
        {
            List lines = FileUtils.readLines(new File(fileName));
            String namesLine = (String)lines.get(0);
            String unitLine = (String)lines.get(1);
            String valueTypeLine = (String)lines.get(2);
            String initialValueLine = (String)lines.get(3);
            String maximumLine = (String)lines.get(4);
            String minimumLine = (String)lines.get(5);
            String namesTokens[] = namesLine.split(",");
            String unitsTokens[] = unitLine.split(",");
            String valueTypeLineTokens[] = valueTypeLine.split(",");
            String maximumLineTokens[] = maximumLine.split(",");
            String minimumLineTokens[] = minimumLine.split(",");
            String initialValueLineTokens[] = initialValueLine.split(",");
            JSONObject homeoboxJSONObject = new JSONObject();
            homeoboxJSONObject.put(TeleonomeConstants.DENEWORD_NAME_ATTRIBUTE, homeboxName);
            JSONArray denesArray = new JSONArray();
            homeoboxJSONObject.put("Denes", denesArray); 
            JSONObject homeoboxIndexDeneJSONObject = new JSONObject();
            homeoboxIndexDeneJSONObject.put(TeleonomeConstants.DENEWORD_NAME_ATTRIBUTE, TeleonomeConstants.SPERM_HOMEOBOX_INDEX);
            homeoboxIndexDeneJSONObject.put(TeleonomeConstants.DENE_DENE_TYPE_ATTRIBUTE, TeleonomeConstants.SPERM_HOMEOBOX_INDEX);
            denesArray.put(homeoboxIndexDeneJSONObject);
            JSONArray homeoboxIndexDeneWordsJSONArray = new JSONArray();
            homeoboxIndexDeneJSONObject.put("DeneWords", homeoboxIndexDeneWordsJSONArray);
            
            
            JSONObject sensorDeneJSONObject = new JSONObject();
            sensorDeneJSONObject.put(TeleonomeConstants.DENEWORD_NAME_ATTRIBUTE, sensorName);
            sensorDeneJSONObject.put(TeleonomeConstants.DENE_DENE_TYPE_ATTRIBUTE, "Sensor");
            sensorDeneJSONObject.put(TeleonomeConstants.SPERM_HOX_DENE_TARGET, (new StringBuilder()).append("@").append(teleonomeName).append(":").append("Internal").append(":").append("Sensors").toString());
            denesArray.put(sensorDeneJSONObject);
            //
            // now put the sensor into the homebox index
            //
            String homeboxpointerValue = "@Sperm:Pacemaker:"+ sensorName +":"+sensorName;
            JSONObject homeoboxIndexDeneWordJSONObject = Utils.createDeneWordJSONObject(sensorName, homeboxpointerValue , null, "Dene Pointer", true);
            homeoboxIndexDeneWordJSONObject.put(TeleonomeConstants.DENEWORD_DENEWORD_TYPE_ATTRIBUTE, TeleonomeConstants.DENEWORD_TYPE_HOX_DENE_POINTER);
            homeoboxIndexDeneWordsJSONArray.put(homeoboxIndexDeneWordJSONObject);
            
            
            JSONArray sensorDeneWordsJSONArray = new JSONArray();
            sensorDeneJSONObject.put("DeneWords", sensorDeneWordsJSONArray);
            JSONObject codonDeneWord = Utils.createDeneWordJSONObject("Codon", sensorName, null, "String", true);
            sensorDeneWordsJSONArray.put(codonDeneWord);
            String pointerMicroControllerValue = (new StringBuilder()).append("@").append(teleonomeName).append(":").append("Internal").append(":").append("Components").append(":").append(microControllerName).toString();
            JSONObject pointerToMicroControllerDeneWord = Utils.createDeneWordJSONObject("Pointer to Microcontroller", pointerMicroControllerValue, null, "Dene Pointer", true);
            pointerToMicroControllerDeneWord.put(TeleonomeConstants.DENEWORD_DENEWORD_TYPE_ATTRIBUTE,TeleonomeConstants.DENEWORD_TYPE_SENSOR_MICROCONTROLLER_POINTER);
            
            sensorDeneWordsJSONArray.put(pointerToMicroControllerDeneWord);
            JSONObject deneValueDefinition = null;
            JSONObject deneReportingDefinition = null;
            String valueName = "";
            for(int i = 0; i < namesTokens.length; i++)
            {
                valueName = (new StringBuilder()).append(namesTokens[i]).append(" Value").toString();
                String pointerToValuePointer = (new StringBuilder()).append("@").append(teleonomeName).append(":").append("Internal").append(":").append("Sensors").append(":").append(valueName).toString();
                JSONObject pointerToValueDeneWord = Utils.createDeneWordJSONObject(valueName, pointerToValuePointer, null, "Dene Pointer", true);
                pointerToValueDeneWord.put(TeleonomeConstants.DENEWORD_DENEWORD_TYPE_ATTRIBUTE, TeleonomeConstants.DENEWORD_TYPE_SENSOR_VALUE);
                sensorDeneWordsJSONArray.put(pointerToValueDeneWord);
                double minimum = -99999;
                double maximum = -99999;
                try
                {
                    minimum = Double.parseDouble(minimumLineTokens[i]);
                }
                catch(NumberFormatException numberformatexception) { }
                try
                {
                    maximum = Double.parseDouble(maximumLineTokens[i]);
                }
                catch(NumberFormatException numberformatexception1) { }
                deneValueDefinition = getSensorValueDene(namesTokens[i], initialValueLineTokens[i], unitsTokens[i], valueTypeLineTokens[i], i + 1, minimum, maximum);
                denesArray.put(deneValueDefinition);
                deneReportingDefinition = getReportingDene(namesTokens[i], initialValueLineTokens[i], unitsTokens[i], valueTypeLineTokens[i], i + 1, minimum, maximum);
                denesArray.put(deneReportingDefinition);
                homeboxpointerValue = "@Sperm:Pacemaker:"+ sensorName +":"+namesTokens[i];
                
//                JSONObject homeoboxIndexDeneWordJSONObject = Utils.createDeneWordJSONObject(namesTokens[i], homeboxpointerValue, null, "Dene Pointer", true);
//                homeoboxIndexDeneWordJSONObject.put(TeleonomeConstants.DENEWORD_DENEWORD_TYPE_ATTRIBUTE, TeleonomeConstants.DENEWORD_TYPE_HOX_DENE_POINTER);
//                homeoboxIndexDeneWordsJSONArray.put(homeoboxIndexDeneWordJSONObject);
                
                 homeoboxIndexDeneWordJSONObject = Utils.createDeneWordJSONObject(namesTokens[i] + " Value", homeboxpointerValue  + " Value", null, "Dene Pointer", true);
                homeoboxIndexDeneWordJSONObject.put(TeleonomeConstants.DENEWORD_DENEWORD_TYPE_ATTRIBUTE, TeleonomeConstants.DENEWORD_TYPE_HOX_DENE_POINTER);
                homeoboxIndexDeneWordsJSONArray.put(homeoboxIndexDeneWordJSONObject);
                
                homeoboxIndexDeneWordJSONObject = Utils.createDeneWordJSONObject(namesTokens[i] , homeboxpointerValue, null, "Dene Pointer", true);
                homeoboxIndexDeneWordJSONObject.put(TeleonomeConstants.DENEWORD_DENEWORD_TYPE_ATTRIBUTE, TeleonomeConstants.DENEWORD_TYPE_HOX_DENE_POINTER);
                homeoboxIndexDeneWordsJSONArray.put(homeoboxIndexDeneWordJSONObject);
                
            }
            
            String outputFileName = Utils.getLocalDirectory() + "SensorHomeBox.json";
            logger.info("saving file to " + outputFileName);
            FileUtils.writeStringToFile(new File(outputFileName), homeoboxJSONObject.toString(4));
            logger.info("process completed");

        }
        catch(IOException e)
        {
            e.printStackTrace();
        }
    }

    private JSONObject getSensorValueDene(String valueName, String initialValue, String unit, String valueType, int reqPos, double minimum, 
            double maximum)
    {
        JSONObject sensorValueDeneJSONObject = new JSONObject();
        sensorValueDeneJSONObject.put(TeleonomeConstants.DENE_DENE_NAME_ATTRIBUTE, (new StringBuilder()).append(valueName).append(" Value").toString());
        sensorValueDeneJSONObject.put(TeleonomeConstants.DENEWORD_TARGET_ATTRIBUTE, (new StringBuilder()).append("@").append(teleonomeName).append(":").append("Internal").append(":").append("Sensors").toString());
        sensorValueDeneJSONObject.put(TeleonomeConstants.DENE_DENE_TYPE_ATTRIBUTE, "Sensor Value Definition");
        JSONArray deneWordsArray = new JSONArray();
        sensorValueDeneJSONObject.put("DeneWords", deneWordsArray);
        JSONObject deneWord = Utils.createDeneWordJSONObject("Codon", sensorName, null, "String", true);
        deneWordsArray.put(deneWord);
        if(!unit.equals("")) {
        	deneWord = Utils.createDeneWordJSONObject(TeleonomeConstants.DENEWORD_UNIT_ATTRIBUTE, unit, null, "String", true);
            deneWordsArray.put(deneWord);
        }
        
        deneWord = Utils.createDeneWordJSONObject(TeleonomeConstants.DENEWORD_VALUETYPE_ATTRIBUTE, valueType, null, "String", true);
        deneWordsArray.put(deneWord);
        deneWord = Utils.createDeneWordJSONObject("Sensor Request Queue Position", Integer.valueOf(reqPos), null, "int", true);
        deneWordsArray.put(deneWord);
        String reportingAddress = (new StringBuilder()).append("@").append(teleonomeName).append(":").append("Purpose").append(":").append("Sensor Data").append(":").append(valueName).append(":").append(valueName).append(" Data").toString();
        deneWord = Utils.createDeneWordJSONObject("Reporting Address", reportingAddress, null, "Dene Pointer", true);
        deneWordsArray.put(deneWord);
        if(maximum != -99999D)
        {
            deneWord = Utils.createDeneWordJSONObject("Range Maximum", Double.valueOf(maximum), null, "double", true);
            deneWordsArray.put(deneWord);
        }
        if(minimum != -99999D)
        {
            deneWord = Utils.createDeneWordJSONObject("Range Minimum", Double.valueOf(minimum), null, "double", true);
            deneWordsArray.put(deneWord);
        }
        return sensorValueDeneJSONObject;
    }

    private JSONObject getReportingDene(String valueName, String initialValue, String unit, String valueType, int reqPos, double minimum, 
            double maximum)
    {
        JSONObject reportingDeneJSONObject = new JSONObject();
        reportingDeneJSONObject.put(TeleonomeConstants.DENE_DENE_NAME_ATTRIBUTE, (new StringBuilder()).append(valueName).toString());
        reportingDeneJSONObject.put(TeleonomeConstants.DENEWORD_TARGET_ATTRIBUTE, (new StringBuilder()).append("@").append(teleonomeName).append(":").append("Purpose").append(":").append("Sensor Data").toString());
        reportingDeneJSONObject.put(TeleonomeConstants.DENE_DENE_TYPE_ATTRIBUTE, "Report");
        JSONArray deneWordsArray = new JSONArray();
        reportingDeneJSONObject.put("DeneWords", deneWordsArray);
        JSONObject deneWord = Utils.createDeneWordJSONObject(valueName + (" Data"), initialValue, unit, valueType, true);
        deneWordsArray.put(deneWord);
        return reportingDeneJSONObject;
    }

    public static void main(String args[])
    {
        new GenerateSensorHomeoboxFromCSV();
    }

    
}
