//************************************************
// 
// Written by Hideo Otsuna (HHMI Janelia inst.)
// Oct 2019
// 
//**************************************************

import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.io.DirectoryChooser;
import ij.io.FileSaver;
import ij.plugin.PlugIn;
import ij.plugin.frame.*; 
import ij.plugin.filter.*;
//import ij.plugin.Macro_Runner.*;
import ij.gui.GenericDialog.*;
import ij.macro.*;
import ij.measure.Calibration;
import ij.plugin.CanvasResizer;
import ij.plugin.Resizer;
import ij.util.Tools;
import ij.io.FileInfo;
import ij.io.TiffEncoder;
import ij.plugin.filter.GaussianBlur;

import java.awt.*;
import java.awt.image.*;
import javax.imageio.*;
import javax.imageio.ImageIO;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.ImageIO;


import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.zip.GZIPOutputStream;
import java.io.IOException;
import java.io.File;
import java.nio.*;
import java.util.*;
import java.util.Iterator;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;


public class subtracting_multithread implements PlugIn {
	
	int thread_num_=0; int Subval=0;
	
	public void run(String arg) {
		
		
		thread_num_=(int)Prefs.get("thread_num_.int",4);
		
		
		GenericDialog gd = new GenericDialog("subtracting multi 2D tiffs");
		
		gd.addNumericField("CPU number", thread_num_, 0);
		gd.addNumericField("Subtracting value", Subval, 0);
		
		gd.showDialog();
		if(gd.wasCanceled()){
			return;
		}
		
		thread_num_ = (int)gd.getNextNumber();
		Subval = (int)gd.getNextNumber();
		
		if(thread_num_ <= 0) thread_num_ = 1;
		Prefs.set("thread_num.int", thread_num_);
		
		DirectoryChooser dirO = new DirectoryChooser("serial tiff for subtraction directory");
		String Odirectory = dirO.getDirectory();
		
		DirectoryChooser dirs = new DirectoryChooser("Save directory");
		String Sdirectory = dirs.getDirectory();
		
		IJ.log("Odirectory; "+Odirectory+"   Sdirectory; "+Sdirectory);
		
		File OdirectoryFile = new File(Odirectory);
		final File names[] = OdirectoryFile.listFiles(); 
		Arrays.sort(names);
		
		IJ.log("names length;"+names.length);
		
		
		long timestart = System.currentTimeMillis();
		Threfunction(Odirectory,names,Sdirectory,thread_num_,Subval);
		
		long timeend = System.currentTimeMillis();
		
		long gapS = (timeend-timestart)/1000;
		
		IJ.log("Done "+gapS+" second");
		
	} //public void run(String arg) {
	
	public void Threfunction (final String FOdirectory, File names[],final String SdirectoryF, final int thread_num_F, final int SubvalF){
		
		final AtomicInteger ai = new AtomicInteger(0);
		final Thread[] threads = newThreadArray();
		
		IJ.log("FOdirectory+names[5].getName();  "+FOdirectory+names[5].getName());
		
		IJ.log("thread_num_F; "+thread_num_F+"  FOdirectory; "+FOdirectory+"  SdirectoryF; "+SdirectoryF);
		
		
		for (int ithread = 0; ithread < threads.length; ithread++) {
			// Concurrently run in as many threads as CPUs
			threads[ithread] = new Thread() {
				
				{ setPriority(Thread.NORM_PRIORITY); }
				
				public void run() {
					
					for(int ii=ai.getAndIncrement(); ii<names.length; ii = ai.getAndIncrement()){
						
						
						//IJ.showProgress((double)iMIP/(double) names.length);
						IJ.showStatus(String.valueOf(ii));
						
						ImagePlus imp =null;// new ImagePlus();
						ImageProcessor ip=null;
						
						int tifposi = names[ii].getName().lastIndexOf("tif");
						
						if(tifposi>0){
							while(imp==null){
								imp = IJ.openImage(FOdirectory+names[ii].getName());
							}
							
							while(ip==null){
								ip = imp.getProcessor();//.duplicate()
							}
							
							int [] info= imp.getDimensions();
							final int width = info[0];//52
							final int height = info[1];//52
							
							int sumpx= width*height;
							
							ImagePlus bigimg =  IJ.createImage(names[ii].getName(), width,height,1, 16);
							
						//	ImageConverter ic2 = new ImageConverter(bigimg);
					//		ic2.convertToGray16();
							
							ImageProcessor ipbig = bigimg.getProcessor();
							
							//		IJ.log("width; "+width+"  height; "+height);
							
							for(int ixypix=0; ixypix<sumpx; ixypix++){// big MIPcreation per a thread
								
								int pix0=ip.get(ixypix);
								
								int setpx=0;
								if(pix0>SubvalF)
								setpx=pix0-SubvalF;
								
								ipbig.set(ixypix,setpx);
								
							}//for(int ixypix=0; ixypix<=sumpx; ixypix++){// big MIPcreation per a thread
							
							
							imp.unlock();
							imp.close();
							
							int packbitsw=0;
							
							if(packbitsw==0){
								
								int dotindex = names[ii].getName().lastIndexOf(".");
								
								String savename=names[ii].getName();
								if(dotindex!=-1)
								savename = names[ii].getName().substring(0,dotindex);
								
								try{ // Your output file or stream
									
									writeImage(bigimg, SdirectoryF+savename+".tif");
								} catch (Exception e) {
									e.printStackTrace();
								}
							}//	if(packbitsw==0){
							
							if(packbitsw==1){
								BufferedImage image = bigimg.getBufferedImage(); // Your input image
								
								
								Iterator<ImageWriter> writer1 = ImageIO.getImageWritersByFormatName("TIFF"); // // Assuming a TIFF plugin is installed
								ImageWriter writer = writer1.next();
								
								try{ // Your output file or stream
									ImageOutputStream out = ImageIO.createImageOutputStream(new File(SdirectoryF+names[ii].getName()));//new FileOutputStream(SdirectoryF+addst+totalslicenum+".tif")
									writer.setOutput(out);
									
									//		IJ.log("file save; "+SdirectoryF+addst+totalslicenum+".tif");
									
									ImageWriteParam param = writer.getDefaultWriteParam();
									param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
									param.setCompressionType("LZW");//PackBits
									
									writer.write(null, new IIOImage(image, null, null), param);//
									
								} catch (IOException e) {
									e.printStackTrace();
								}
								
								writer.dispose();
							}
							//	bigimg.flush();
							bigimg.unlock();
							bigimg.close();
							
							
						}//	if(tifposi>0){
						
					}//for(int ii=ai.getAndIncrement(); ii<FXYtotalbrick; ii = ai.getAndIncrement()){
			}};
		}//	for (int ithread = 0; ithread < threads.length; ithread++) {
		startAndJoin(threads);
		
	}
	
	private Thread[] newThreadArray() {
		int n_cpus = Runtime.getRuntime().availableProcessors();
		if (n_cpus > thread_num_) n_cpus = thread_num_;
		if (n_cpus <= 0) n_cpus = 1;
		return new Thread[n_cpus];
	}
	
	public static void startAndJoin(Thread[] threads)
	{
		for (int ithread = 0; ithread < threads.length; ++ithread)
		{
			threads[ithread].setPriority(Thread.NORM_PRIORITY);
			threads[ithread].start();
		}
		
		try
		{   
			for (int ithread = 0; ithread < threads.length; ++ithread)
			threads[ithread].join();
		} catch (InterruptedException ie)
		{
			throw new RuntimeException(ie);
		}
	}
	
	public void writeImage (ImagePlus imp, String path) throws Exception{
		write16gs(imp, path);
		
	}
	void write16gs(ImagePlus imp, String path) throws Exception {
		ShortProcessor sp = (ShortProcessor)imp.getProcessor();
		BufferedImage bi = sp.get16BitBufferedImage();
		File f = new File(path);
		ImageIO.write(bi, "png", f);
	}
}




