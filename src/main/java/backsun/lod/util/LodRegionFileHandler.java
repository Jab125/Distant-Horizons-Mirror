package backsun.lod.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import backsun.lod.objects.LodChunk;
import backsun.lod.objects.LodRegion;

/**
 * This object handles creating LodRegions
 * from files and saving LodRegion objects
 * to file.
 * 
 * @author James Seibel
 * @version 09-27-2020
 */
public class LodRegionFileHandler
{
	private final String SAVE_DIR = "C:/Users/James Seibel/Desktop/lod_save_folder/";
	
	private final String FILE_NAME_PREFIX = "lod";
	private final String FILE_NAME_DELIMITER = "-";
	private final String FILE_EXTENSION = ".txt";
	
	
	public LodRegionFileHandler()
	{
		
	}
	
	
	
	
	
	
	/**
	 * Return the LodRegion that 
	 */
	public LodRegion loadRegionFromFile(int regionX, int regionZ)
	{
		String fileName = getFileNameForRegion(regionX, regionZ);
		
		File f = new File(fileName);
		
		if (!f.exists())
		{
			// there wasn't a file, don't
			// return anything
			return null;
		}
		
		
		LodRegion region = new LodRegion(regionX, regionZ);
		
		
		try
		{
			BufferedReader br = new BufferedReader(new FileReader(f));
			
			String s = br.readLine();
			
			while(s != null || !s.isEmpty())
			{
				// convert each line into a LOD object and add it to the region
			}
			
			
			br.close();
		}
		catch (IOException e)
		{
			// File not found
			
			// or the buffered reader encountered a 
			// problem reading the file
			
			return null;
		}
		
		
		
		return null;
	}
	
	
	
	
	
	public void saveRegionToDisk(LodChunk chunk)
	{
		// convert chunk coordinates to region
		// coordinates
		int x = (int) (chunk.x / 32.0);
		int z = (int) (chunk.z / 32.0);
		
		File f = new File(getFileNameForRegion(x, z));
		
		
		try
		{
			if (!f.exists())
			{
				f.createNewFile();
			}
			
			FileWriter fw = new FileWriter(f); // true means append to file
			String data = chunk.x + "\t" + chunk.z;
			
			fw.write(data + "\n");
			fw.close();
			
			System.out.println("LOD \t" + data);
		}
		catch(Exception e)
		{
			System.err.println("LOD ERROR \t" + e);
		}
	}
	
	
	
	
	

	/**
	 * Returns true if a file exists for the region
	 * containing the given chunk.
	 */
	private boolean regionFileExistForChunk(int chunkX, int chunkZ)
	{
		// convert chunk coordinates to region
				// coordinates
		int regionX = (int) (chunkX / 32.0);
		int regionZ = (int) (chunkZ / 32.0);
		
		return new File(getFileNameForRegion(regionX, regionZ)).exists();
	}
	
	/**
	 * Returns true if a file exists
	 * for the given region coordinates.
	 */
	private boolean regionFileExistForRegion(int regionX, int regionZ)
	{
		return new File(getFileNameForRegion(regionX, regionZ)).exists();
	}
	
	
	
	/**
	 * Return the name of the file that should contain the 
	 * region at the given x and z.
	 * @param regionX
	 * @param regionZ
	 */
	private String getFileNameForRegion(int regionX, int regionZ)
	{
		return SAVE_DIR + 
				FILE_NAME_PREFIX + FILE_NAME_DELIMITER + 
				regionX + FILE_NAME_DELIMITER + regionZ + FILE_EXTENSION;
	}
}
