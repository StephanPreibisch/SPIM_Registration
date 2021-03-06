package spim.fiji.spimdata.imgloaders;

import java.io.File;
import java.nio.ByteBuffer;

import loci.formats.in.SlideBook6Reader;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
//import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayCursor;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import spim.fiji.datasetmanager.SlideBook6;

import org.scijava.nativelib.NativeLibraryUtil;

public class SlideBook6ImgLoader extends AbstractImgLoader
{
	final File sldFile;
	final AbstractSequenceDescription< ? extends BasicViewSetup, ? extends BasicViewDescription< ? >, ? > sequenceDescription;
	
	// -- Static initializers --

	private static boolean libraryFound = false;
	
	public SlideBook6ImgLoader(
			final File sldFile,
			final ImgFactory< ? extends NativeType< ? > > imgFactory,
			final AbstractSequenceDescription< ? extends BasicViewSetup, ? extends BasicViewDescription< ? >, ? > sequenceDescription )
	{
		super();
		this.sldFile = sldFile;
		this.sequenceDescription = sequenceDescription;

		try {
			// load JNI wrapper of SBReadFile.dll
			if (!libraryFound) {
				libraryFound = NativeLibraryUtil.loadNativeLibrary(SlideBook6ImgLoader.class, "SlideBook6Reader");
			}
		}
		catch (UnsatisfiedLinkError e) {
			// log level debug, otherwise a warning will be printed every time a file is initialized without the .dll present
			IOFunctions.println("3i SlideBook SlideBook6Reader native library not found.");
			libraryFound = false;
		}
		catch (SecurityException e) {
			IOFunctions.println("Insufficient permission to load native library");
			libraryFound = false;
		}
		
		setImgFactory( imgFactory );
	}

	public File getFile() { return sldFile; }

	final public < T extends RealType< T > & NativeType< T > > void populateImage( final ArrayImg< T, ? > img, final BasicViewDescription< ? > vd, final SlideBook6Reader r)
	{
		final ArrayCursor< T > cursor = img.cursor();
		
		final int t = vd.getTimePoint().getId();
		final int a = vd.getViewSetup().getAttribute( Angle.class ).getId();
		final int c = vd.getViewSetup().getAttribute( Channel.class ).getId();
		// final int i = vd.getViewSetup().getAttribute( Illumination.class ).getId();
		
		final int bpp = r.getBytesPerPixel(a);

		byte[] data = new byte[(int) (bpp * img.size())];
		
		ByteBuffer buffer = ByteBuffer.wrap(data);

		for ( int z = 0; z < img.dimension(2); ++z )
		{
			// SlideBook6Reader.dll
			// a = angle id (SPIMdata) = capture index (SlideBook)
			r.readImagePlaneBuf(data, a, 0, t, z, c);
			
			// TODO: handle endian conversion
			for ( final char v : buffer.asCharBuffer().array() )
				cursor.next().setReal( v );
		}
	}

	@Override
	public RandomAccessibleInterval< FloatType > getFloatImage( final ViewId view, final boolean normalize )
	{
		try
		{
			// SlideBook6Reader.dll
			SlideBook6Reader reader = new SlideBook6Reader();

			reader.openFile(sldFile.getPath());
			
			final BasicViewDescription< ? > vd = sequenceDescription.getViewDescriptions().get( view );
			final int a = vd.getViewSetup().getAttribute( Angle.class ).getId();
			final int w = reader.getNumXColumns(a);
			final int h = reader.getNumYRows(a);
			final int d = reader.getNumZPlanes(a);
			final ArrayImg< FloatType, ? > img = ArrayImgs.floats( w, h, d );
			
			populateImage( img, vd, reader );

			if ( normalize )
				normalize( img );

			// TODO: make sure a < getNumCaptures()
			float voxelSize = reader.getVoxelSize(a);
			float zSpacing = 1;
			if (reader.getNumZPlanes(a) > 1) {
				zSpacing = (float) (reader.getZPosition(a, 0, 1) - reader.getZPosition(a, 0, 0));
			}
			
			updateMetaDataCache( view, w, h, d, voxelSize, voxelSize, zSpacing );

			// SlideBook6Reader.dll
			reader.closeFile();

			return img;
		}
		catch ( Exception e )
		{
			IOFunctions.println( "Failed to load viewsetup=" + view.getViewSetupId() + " timepoint=" + view.getTimePointId() + ": " + e );
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public RandomAccessibleInterval< UnsignedShortType > getImage( final ViewId view )
	{
		try
		{
			// SlideBook6Reader.dll
			SlideBook6Reader reader = new SlideBook6Reader();

			reader.openFile(sldFile.getPath());
			final BasicViewDescription< ? > vd = sequenceDescription.getViewDescriptions().get( view );
			final int a = vd.getViewSetup().getAttribute( Angle.class ).getId();
			final int w = reader.getNumXColumns(a);
			final int h = reader.getNumYRows(a);
			final int d = reader.getNumZPlanes(a);
			final ArrayImg< UnsignedShortType, ? > img = ArrayImgs.unsignedShorts( w, h, d );

			populateImage( img, vd, reader );
			
			// TODO: make sure a < getNumCaptures()
			float voxelSize = reader.getVoxelSize(a);
			float zSpacing = 1;
			if (reader.getNumZPlanes(a) > 1) {
				zSpacing = (float) (reader.getZPosition(a, 0, 1) - reader.getZPosition(a, 0, 0));
			}
			
			updateMetaDataCache( view, w, h, d, voxelSize, voxelSize, zSpacing );

			// SlideBook6Reader.dll
			reader.closeFile();

			return img;
		}
		catch ( Exception e )
		{
			IOFunctions.println( "Failed to load viewsetup=" + view.getViewSetupId() + " timepoint=" + view.getTimePointId() + ": " + e );
			e.printStackTrace();
			return null;
		}
	}

	@Override
	protected void loadMetaData( final ViewId view )
	{
		// SlideBook6Reader.dll
		SlideBook6Reader reader = new SlideBook6Reader();

		try
		{
			reader.openFile(sldFile.getPath());

			final BasicViewDescription< ? > vd = sequenceDescription.getViewDescriptions().get( view );
			final int a = vd.getViewSetup().getAttribute( Angle.class ).getId();
			final int w = reader.getNumXColumns(a);
			final int h = reader.getNumYRows(a);
			final int d = reader.getNumZPlanes(a);

			// TODO: make sure a < getNumCaptures()
			float voxelSize = reader.getVoxelSize(a);
			float zSpacing = 1;
			if (reader.getNumZPlanes(a) > 1) {
				zSpacing = (float) (reader.getZPosition(a, 0, 1) - reader.getZPosition(a, 0, 0));
			}

			updateMetaDataCache( view, w, h, d, 
					voxelSize, voxelSize, zSpacing );

			// SlideBook6Reader.dll
			reader.close();
		}
		catch ( Exception e )
		{
			IOFunctions.println( "Failed to load metadata for viewsetup=" + view.getViewSetupId() + " timepoint=" + view.getTimePointId() + ": " + e );
			e.printStackTrace();
		}
	}

	@Override
	public String toString()
	{
		return new SlideBook6().getTitle() + ", ImgFactory=" + imgFactory.getClass().getSimpleName();
	}
		
}
