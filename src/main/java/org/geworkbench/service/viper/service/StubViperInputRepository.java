package org.geworkbench.service.viper.service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Random;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geworkbench.service.viper.schema.ViperInput;

public class StubViperInputRepository implements ViperInputRepository {

    private static final Log logger = LogFactory.getLog(StubViperInputRepository.class);
    
    private static final String VIPERROOT = "/ifs/data/c2b2/af_lab/cagrid/r/viper/runs/";
	private static final String scriptDir = "/ifs/data/c2b2/af_lab/cagrid/r/viper/scripts/";
	private static final String rscript   = "/nfs/apps/R/2.14.0/bin/Rscript";
	private static final String account   = "cagrid";
	private static final String submitSh  = "viper_submit.sh";
	private static final String viperR    = "viper_starter.r";
	private static final String viperPkg  = "viper.tar.gz";
	private static final String logExt    = ".log";		//viper log file
	private static final String rmaExt    = ".rma";		//viper output tfa in rma format
	private static final long POLL_INTERVAL = 20000;    //20 seconds
	private static final Random random = new Random();

    public String storeViperInput(ViperInput input) throws IOException {
    	String dataDir = getDataDir();
    	if (dataDir==null) {
    		logger.error("Cannot find data dir to store viper input");
    		return null;
    	}
    	
    	DataHandler handler = input.getExpfile();
    	String fname = dataDir+input.getName();
    	File expfile = new File(fname);
    	OutputStream os = new FileOutputStream(expfile);
    	handler.writeTo(os);
    	os.close();
    	logger.info("Storing viper input " + fname + " [" + expfile.exists() + "]");
        return dataDir;
    }
    
    public DataHandler execute(ViperInput input, String dataDir, StringBuilder log) throws IOException {
    	String name = input.getName();    	
    	String runid = new File(dataDir).getName();
    	String prefix = name.substring(0, name.lastIndexOf("."));
    	String outfname = prefix + rmaExt;
    	String logfname = prefix + logExt;
    	String submitStr = submitBase + dataDir + logfname + " -N " + runid + "\n" +
    			rscript + " " + scriptDir + viperR + " " + scriptDir + viperPkg + " " + 
    			dataDir + input.getName() + " " + dataDir + outfname + " " +
    			input.getRegulon() + " " + input.getRegtype() + " " +
    			input.getMethod()  + " " + input.getRlibpath();
    	
    	String submitFile = dataDir + submitSh;
    	if (!writeToFile(submitFile, submitStr)){
    		String msg = "Cannot find write viper job submit script";
    		logger.error(msg);
    		log.append(msg);
    		return null;
    	}
    	int ret = submitJob(submitFile);
    	if (ret!=0){
    		String msg = "Viper job "+runid+" submission error\n";
    		logger.error(msg);
    		log.append(msg);
    		return null;
    	}

    	File resultfile = new File(dataDir + outfname);
		while(!isJobDone(runid)){
		    try{
		    	Thread.sleep(POLL_INTERVAL);
		    }catch(InterruptedException e){
		    }
		}
		if (!resultfile.exists()){
		    String err = null;
		    if ((err = runError(dataDir + logfname)) != null){
		    	String msg = "Viper job "+runid+" abnormal termination\n"+err;
		    	logger.error(msg);
		    	log.append(msg);
		    }else{
		    	String msg = "Viper job "+runid+" was killed";
		    	logger.error(msg);
		    	log.append(msg);
		    }
		    return null;
		}

        logger.info("Sending viper output " + name);
        DataSource source = new FileDataSource(resultfile);
        return new DataHandler(source);
    }

    private static final String maxmem = "4G";
	private static final String timeout = "48::";
    private String submitBase = "#!/bin/bash\n#$ -l mem="+maxmem+",time="+timeout+" -cwd -j y -o ";
    
    private String getDataDir(){
		File root = new File(VIPERROOT);
		if (!root.exists() && !root.mkdir()) return null;

		int i = 0;
		String dirname = null;
		File randdir = null;
		try{
		    do{
		    	dirname = VIPERROOT + "vpr" + random.nextInt(Short.MAX_VALUE)+ "/";
		    	randdir = new File(dirname);
		    }while(randdir.exists() && ++i < Short.MAX_VALUE);
		}catch(Exception e){
		    e.printStackTrace();
		    return null;
		}
		if (i < Short.MAX_VALUE){
			if (!randdir.mkdir()) return null;
			return dirname;
		}
		else return null;
	}
    
    private boolean writeToFile(String fname, String string){
	    BufferedWriter bw = null;
	    try{
			bw = new BufferedWriter(new FileWriter(fname));
			bw.write(string);
			bw.flush();
	    }catch(IOException e){
	    	e.printStackTrace();
	    	return false;
	    }finally{
			try{
			    if (bw!=null) bw.close();
			}catch(IOException e){
			    e.printStackTrace();
			}
	    }
	    return true;
	}
    
	private int submitJob(java.lang.String jobfile){
		String command = "qsub " + jobfile;
		System.out.println(command);
		try {
			Process p = Runtime.getRuntime().exec(command);
			StreamGobbler out = new StreamGobbler(p.getInputStream(), "INPUT");
			StreamGobbler err = new StreamGobbler(p.getErrorStream(), "ERROR");
			out.start();
			err.start();
			return p.waitFor();
		} catch (Exception e) {
			return -1;
		}
	}
	
	private boolean isJobDone(String runid) {
		String cmd = "qstat -u "+account;
		BufferedReader brIn = null;
		BufferedReader brErr = null;
		try{
			Process p = Runtime.getRuntime().exec(cmd);
			brIn = new BufferedReader(new InputStreamReader(p.getInputStream()));
			brErr = new BufferedReader(new InputStreamReader(p.getErrorStream()));
			String line = null;
			while ((line = brIn.readLine())!=null || (line = brErr.readLine())!=null){
				String[] toks = line.trim().split("\\s+");
				if (toks.length > 3 && toks[2].equals(runid))
					return false;
			}
		}catch(Exception e){
			e.printStackTrace();
			return true;
		}finally {
			try{
				if (brIn!=null)  brIn.close();
				if (brErr!=null) brErr.close();
			}catch(Exception e){
				e.printStackTrace();
			}
		}
		return true;
	}

	private String runError(String logfname){
		StringBuilder str = new StringBuilder();
		BufferedReader br = null;
		boolean error = false;
		File logFile = new File(logfname);
		if (!logFile.exists()) return null;
		try{
			br = new BufferedReader(new FileReader(logFile));
			String line = null;
			int i = 0;
			while((line = br.readLine())!=null){
				if (((i = line.indexOf("Error"))>-1)){
					str.append(line.substring(i)+"\n");
					error = true;
				}
			}
		}catch(IOException e){
			e.printStackTrace();
		}finally{
			try{
				if (br!=null) br.close();
			}catch(Exception e){
				e.printStackTrace();
			}
		}
		if (error)  return str.toString();
		return null;
	}

	public static class StreamGobbler extends Thread
	{
	    private InputStream is;
	    private String type;
	    private OutputStream os;
	    
	    StreamGobbler(InputStream is, String type)
	    {
	        this(is, type, null);
	    }
	    StreamGobbler(InputStream is, String type, OutputStream redirect)
	    {
	        this.is = is;
	        this.type = type;
	        this.os = redirect;
	    }
	    
	    public void run()
	    {
            PrintWriter pw = null;
            BufferedReader br = null;
	        try {
	            if (os != null)
	                pw = new PrintWriter(os, true);
	                
	            InputStreamReader isr = new InputStreamReader(is);
	            br = new BufferedReader(isr);
	            String line=null;
	            while ( (line = br.readLine()) != null)
	            {
	                if (pw != null){
	                    pw.println(line);
	                }
	                System.out.println(type + ">" + line);    
	            }
	        } catch (IOException ioe) {
	            ioe.printStackTrace();  
	        } finally {
	        	try{
		        	if (pw!=null) pw.close();
	        		if (br!=null) br.close();
	            }catch(Exception e){
	            	e.printStackTrace();
	            }
	        }
	    }
	}
}
