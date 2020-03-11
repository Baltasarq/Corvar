package com.devbaltasarq.corvar.core;

import android.content.Context;
import android.os.Environment;
import android.util.JsonReader;
import android.util.Log;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.function.Function;


/** Relates the database of JSON files to objects. */
public final class Orm {
    private static final String LogTag = Orm.class.getSimpleName();

    private static final String FIELD_ID = Id.FIELD;
    public static final String FIELD_DATE = "date";
    public static final String FIELD_RR = "rr";
    public static final String FIELD_RRS = "rrs";
    public static final String FIELD_TAG = Tag.FIELD;
    public static final String FIELD_TIME = Duration.FIELD;
    public static final String FIELD_TYPE_ID = Persistent.TypeId.FIELD;

    private static final String DIR_DB = "db";
    private static final String FILE_NAME_PART_SEPARATOR = "-";

    /** Prepares the ORM to operate. */
    private Orm(Context context)
    {
        this.context = context;
        this.reset();
    }

    /** Forces a reset of the ORM, so the contents of the store is reflected
      *  in the internal data structures.
      */
    public final void reset()
    {
        Log.i( LogTag, "Preparing store..." );

        this.dirFiles = this.context.getFilesDir();

        this.createDirectories();
        this.removeCache();
        this.createCaches();

        Log.i( LogTag, "Store ready at: " + this.dirDb.getAbsolutePath() );
        Log.i( LogTag, "    #result files: " + this.filesResult.size() );
    }

    /** Creates the needed directories, if do not exist. */
    private void createDirectories()
    {
        this.dirDb = this.context.getDir( DIR_DB,  Context.MODE_PRIVATE );
        this.dirTmp = this.context.getCacheDir();
    }

    /** Removes all files in cache. */
    public void removeCache()
    {
        if ( this.dirTmp != null ) {
            removeTreeAt( this.dirTmp );
        }

        this.dirTmp = this.context.getCacheDir();
    }

    /** Create the cache of files. */
    private void createCaches()
    {
        final File[] fileList = this.dirDb.listFiles();
        this.filesResult = new HashSet<>();

        for (File f: fileList) {
            final Persistent.TypeId typeId = this.getTypeIdForExt( f );

            this.addToCache( f );
        }

        return;
    }

    /** @return all results. */
    public File[] getAllResults()
    {
        return this.filesResult.toArray( new File[ 0 ] );
    }

    /** Removes the object from cache.
      * @param r The object to remove from cache.
      */
    private void removeFromCache(Result r)
    {
        this.removeFromCache( new File( this.dirDb, r.getResultFileName() ) );
    }

    /** Remove a file from the cache.
      *  @param f Tje file to remove.
      */
    private void removeFromCache(File f)
    {
        this.filesResult.remove( f );
    }

    /** Add a file to the general cache.
      * @param f The file to add.
      */
    private void addToCache(File f)
    {
        this.filesResult.add( f );
    }

    /** @return true if the file exists in the cache, false otherwise. */
    private boolean existsInCache(File f)
    {
        return this.filesResult.contains( f );
    }

    /** Decide the type for a file, following the extension.
     *
     * @param f the file to decide the type for.
     * @return the typeid for that file.
     * @see Persistent
     */
    private Persistent.TypeId getTypeIdForExt(File f)
    {
        final String ExtResult = getFileExtFor( Persistent.TypeId.Result );
        Persistent.TypeId toret = null;

        if ( f.getName().endsWith( ExtResult ) ) {
            toret = Persistent.TypeId.Result;
        }
        else {
            throw new IllegalArgumentException( "file is not from a recognized type" );
        }

        return toret;
    }

    /** Extracts the id from the file name.
      * Whatever this file is, the id between the '.' for the ext and the '-' separator.
      * @param file the file to extract the id from.
      * @return A long with the id.
      * @throws Error if the file name does not contain an id.
      */
    private long parseIdFromFile(File file)
    {
        final String fileName = file.getName();
        final int separatorPos = fileName.indexOf( FILE_NAME_PART_SEPARATOR );
        long toret;

        if ( separatorPos >= 0 ) {
            int extSeparatorPos = fileName.lastIndexOf( '.' );

            if ( extSeparatorPos < 0 ) {
                extSeparatorPos = fileName.length();
            }

            final String id = fileName.substring( separatorPos + 1, extSeparatorPos );

            try {
                toret = Long.parseLong( id );
            } catch(NumberFormatException exc) {
                throw new Error( "parseIdFromFile: malformed id: " + id );
            }
        } else {
            throw new Error( "parseIdFromFile: separator not found in file name: " + fileName );
        }

        return toret;
    }

    /** Removes object 'p' from the database.
      * @param r The result to remove.
      */
    public void remove(Result r)
    {
        // Remove main object
        final File REMOVE_FILE = new File( this.dirDb, r.getResultFileName() );

        this.removeFromCache( r );

        if ( !REMOVE_FILE.delete() ) {
            Log.e( LogTag, "Error removing file: " + REMOVE_FILE );
        }

        Log.d( LogTag, "Result deleted: " + r.getId() );
    }

    /** @return a newly created temp file. */
    public File createTempFile(String prefix, String suffix) throws IOException
    {
        return File.createTempFile( prefix, suffix, this.dirTmp );
    }

    /** Stores any data object.
     * @param r The result object to store.
     */
    public void store(Result r) throws IOException
    {
        this.store( this.dirDb, r );
    }

    private void store(File dir, Result r) throws IOException
    {
        // Store the data
        final File TEMP_FILE = this.createTempFile(
                                    r.getTypeId().toString(),
                                    r.getId().toString() );
        final File DATA_FILE = new File( dir, r.getResultFileName() );
        Writer writer = null;

        try {
            Log.i( LogTag, "Storing: " + r.toString() + " to: " + DATA_FILE.getAbsolutePath() );
            writer = openWriterFor( TEMP_FILE );
            r.toJSON( writer );
            close( writer );

            if ( !TEMP_FILE.renameTo( DATA_FILE ) ) {
                Log.d( LogTag, "Unable to move: " + DATA_FILE );
                Log.d( LogTag, "Trying to copy: " + TEMP_FILE + " to: " + DATA_FILE );
                copy( TEMP_FILE, DATA_FILE );
            }
            this.addToCache( DATA_FILE );
            Log.i( LogTag, "Finished storing." );
        } catch(IOException exc) {
            final String msg = "I/O error writing: "
                            + DATA_FILE.toString() + ": " + exc.getMessage();
            Log.e( LogTag, msg );
            throw new IOException( msg );
        } catch(JSONException exc) {
            final String msg = "error creating JSON for: "
                            + DATA_FILE.toString() + ": " + exc.getMessage();
            Log.e( LogTag, msg );
            throw new IOException( msg );
        } finally {
          close( writer );
          if ( !TEMP_FILE.delete() ) {
              Log.e( LogTag, "Error removing file: " + TEMP_FILE );
          }
        }
    }

    /** Exports a given result set.
     * @param dir the directory to export the result set to.
     *             If null, then Downloads is chosen.
     * @param res the result to export.
     * @throws IOException if something goes wrong, like a write fail.
     */
    public void exportResult(File dir, Result res) throws IOException
    {
        final String RES_FILE_NAME = res.getResultFileName();

        if ( dir == null ) {
            dir = DIR_DOWNLOADS;
        }

        try {
            final String BASE_FILE_NAME = removeFileExt( RES_FILE_NAME );
            final String RRS_FILE_NAME = "rrs-" + BASE_FILE_NAME + ".rr.txt";

            // Org
            final File ORG_FILE = new File( this.dirDb, RES_FILE_NAME );
            this.store( res );

            // Dest
            final File OUTPUT_FILE = new File( dir, RES_FILE_NAME );
            final File RR_OUTPUT_FILE = new File( dir, RRS_FILE_NAME );
            final Writer RRS_STREAM = openWriterFor( RR_OUTPUT_FILE );

            dir.mkdirs();
            copy( ORG_FILE, OUTPUT_FILE );
            res.exportToStdTextFormat( RRS_STREAM );

            close( RRS_STREAM );
        } catch(IOException exc) {
            throw new IOException(
                    "exporting: '"
                            + RES_FILE_NAME
                            + "' to '" + dir
                            + "': " + exc.getMessage() );
        }

        return;
    }

    public File getFileById(Id id, Persistent.TypeId typeId)
    {
        File toret = null;

        for(File rf: this.filesResult) {
            if ( id.get() == parseIdFromFile( rf ) ) {
                toret = rf;
                break;
            }
        }

        return toret;
    }

    /** Retrieves a single object from a table, given its id.
     * @param id The id of the object to retrieve.
     * @param typeId The id of the type of the object.
     * @return A Persistent object.
     * @see Persistent
     */
    public Persistent retrieve(Id id, Persistent.TypeId typeId) throws IOException
    {
        Persistent toret;
        final File DATA_FILE = this.getFileById( id, typeId );
        Reader reader = null;

        try {
            reader = openReaderFor( DATA_FILE );

            toret = Persistent.fromJSON( typeId, reader );
            Log.i( LogTag, "Retrieved: " + toret.toString() + " from: "
                            + DATA_FILE.getAbsolutePath()  );
        } catch(IOException exc) {
            final String msg = "I/O error reading: "
                            + DATA_FILE.toString() + ": " + exc.getMessage();
            Log.e( LogTag, msg );
            throw new IOException( msg );
        } catch(JSONException exc) {
            final String msg = "error reading JSON for: "
                    + DATA_FILE.toString() + ": " + exc.getMessage();
            Log.e( LogTag, msg );
            throw new IOException( msg );
        } finally {
            close( reader );
        }

        return toret;
    }

    /** @return the result object loaded from file f. */
    private static Result retrievePartialObject(File f) throws IOException
    {
        Result toret;
        Reader reader = null;

        try {
            reader = openReaderFor( f );
            toret = Result.fromJSON( reader );
        } catch(IOException|JSONException exc)
        {
            final String msg = "retrieveResult(f) reading JSON: " + exc.getMessage();
            Log.e( LogTag, msg );

            throw new IOException( msg );
        } finally {
            close( reader );
        }

        return toret;
    }

    public static Writer openWriterFor(File f) throws IOException
    {
        BufferedWriter toret;

        try {
            final OutputStreamWriter outputStreamWriter = new OutputStreamWriter(
                    new FileOutputStream( f ),
                    Charset.forName( "UTF-8" ).newEncoder() );

            toret = new BufferedWriter( outputStreamWriter );
        } catch (IOException exc) {
            Log.e( LogTag,"Error creating writer for file: " + f );
            throw exc;
        }

        return toret;
    }

    public static BufferedReader openReaderFor(File f) throws IOException
    {
        BufferedReader toret;

        try {
            toret = openReaderFor( new FileInputStream( f ) );
        } catch (IOException exc) {
            Log.e( LogTag,"Error creating reader for file: " + f.getName() );
            throw exc;
        }

        return toret;
    }

    private static BufferedReader openReaderFor(InputStream inStream)
    {
        final InputStreamReader inputStreamReader = new InputStreamReader(
                inStream,
                Charset.forName( "UTF-8" ).newDecoder() );

        return new BufferedReader( inputStreamReader );
    }

    /** Closes a writer stream. */
    public static void close(Writer writer)
    {
        try {
            if ( writer != null ) {
                writer.close();
            }
        } catch(IOException exc)
        {
            Log.e( LogTag, "closing writer: " + exc.getMessage() );
        }
    }

    /** Closes a reader stream. */
    public static void close(Reader reader)
    {
        try {
            if ( reader != null ) {
                reader.close();
            }
        } catch(IOException exc)
        {
            Log.e( LogTag, "closing reader: " + exc.getMessage() );
        }
    }

    /** Closes a JSONReader stream. */
    private static void close(JsonReader jsonReader)
    {
        try {
            if ( jsonReader != null ) {
                jsonReader.close();
            }
        } catch(IOException exc)
        {
            Log.e( LogTag, "closing json reader: " + exc.getMessage() );
        }
    }

    /** Copies a given file to a destination, overwriting if necessary.
      * @param source The File object of the source file.
      * @param dest The File object of the destination file.
      * @throws IOException if something goes wrong while copying.
      */
    private static void copy(File source, File dest) throws IOException
    {
        final String errorMsg = "error copying: " + source + " to: " + dest + ": ";
        InputStream is;
        OutputStream os;

        try {
            is = new FileInputStream( source );
            os = new FileOutputStream( dest );

            copy( is, os );
        } catch(IOException exc)
        {
            Log.e( LogTag, errorMsg + exc.getMessage() );
            throw new IOException( errorMsg );
        }

        return;
    }

    /** Copies data from a given stream to a destination File, overwriting if necessary.
     * @param is The input stream object to copy from.
     * @param dest The File object of the destination file.
     * @throws IOException if something goes wrong while copying.
     */
    private static void copy(InputStream is, File dest) throws IOException
    {
        final String errorMsg = "error copying input stream -> " + dest + ": ";
        OutputStream os;

        try {
            os = new FileOutputStream( dest );

            copy( is, os );
        } catch(IOException exc)
        {
            Log.e( LogTag, errorMsg + exc.getMessage() );
            throw new IOException( errorMsg );
        }

        return;
    }

    /** Copies from a stream to another one.
     * @param is The input stream object to copy from.
     * @param os The output stream object of the destination.
     * @throws IOException if something goes wrong while copying.
     */
    private static void copy(InputStream is, OutputStream os) throws IOException
    {
        final byte[] buffer = new byte[1024];
        int length;

        try {
            while ( ( length = is.read( buffer ) ) > 0 ) {
                os.write( buffer, 0, length );
            }
        } finally {
            try {
                if ( is != null ) {
                    is.close();
                }

                if ( os != null ) {
                    os.close();
                }
            } catch(IOException exc) {
                Log.e( LogTag, "Copying file: error closing streams: " + exc.getMessage() );
            }
        }

        return;
    }

    /** Removes a whole dir and all its subdirectories, recursively.
      * @param dir The directory to remove.
      */
    private static void removeTreeAt(File dir)
    {
        if ( dir != null
          && dir.isDirectory() )
        {
            final String[] allFiles = dir.list();

            if ( allFiles != null ) {
                for(String fileName: allFiles) {
                    File f = new File( dir, fileName );

                    if ( f.isDirectory() ) {
                        removeTreeAt( f );
                    }

                    if ( !f.delete() ) {
                        Log.e( LogTag, "Error deleting directory: " + f );
                    }
                }

                if ( !dir.delete() ) {
                    Log.e( LogTag, "Error deleting directory: " + dir );
                }
            }
        } else {
            Log.d( LogTag, "removeTreeAt: directory null or not a directory?" );
        }

        return;
    }

    /** @return the file extension, extracted from param.
     * @param file The file, as a File.
     */
    public static String extractFileExt(File file)
    {
        return extractFileExt( file.getPath() );
    }

    /** @return the file extension, extracted from param.
      * @param fileName The file name, as a String.
      */
    public static String extractFileExt(String fileName)
    {
        String toret = "";

        if ( fileName != null
          && !fileName.trim().isEmpty() )
        {
            final int posDot = fileName.trim().lastIndexOf( "." );


            if ( posDot >= 0
              && posDot < ( fileName.length() - 1 ) )
            {
                toret = fileName.substring( posDot + 1 );
            }
        }

        return toret;
    }

    /** @return the given file name, after extracting the extension.
      * @param fileName the file name to remove the extension from.
      * @return the file name without extension.
      */
    public static String removeFileExt(String fileName)
    {
        final int DOT_POS = fileName.lastIndexOf( '.' );
        String toret = fileName;

        if ( DOT_POS > -1 ) {
            toret = fileName.substring(0, DOT_POS);
        }

        return toret;
    }

    /** Returns the extension for the corresponding data file name.
     * Note that it will be of three chars, lowercase.
     *
     * @param typeId the typeId id of the object.
     * @return the corresponding extension, as a string.
     * @see Persistent
     */
    public static String getFileExtFor(Persistent.TypeId typeId)
    {
        String toret = typeId.toString().substring( 0, 3 ).toLowerCase();

        return toret;
    }

    /** Gets the already open database.
     * @return The Orm singleton object.
     */
    public static Orm get()
    {
        if ( instance == null ) {
            final String ERROR_MSG = "Orm database manager not created yet.";

            Log.e( LogTag, ERROR_MSG );
            throw new IllegalArgumentException( ERROR_MSG );
        }

        return instance;
    }

    /** Initialises the already open database.
     * @param context the application context this database will be working against.
     * @param fileNameAdapter A lambda or referenced method of the signature: (String x) -> x
     *                        this will convert file names to the needed standard, which is
     *                        lowercase and no spaces ('_' instead).
     * @see Function
     */
    public static void init(Context context, FileNameAdapter fileNameAdapter)
    {
        if ( instance == null ) {
            instance = new Orm( context );
        }

        Orm.fileNameAdapter = fileNameAdapter;
        return;
    }

    private HashSet<File> filesResult;
    private File dirFiles;
    private File dirDb;
    private File dirTmp;
    private Context context;

    private static FileNameAdapter fileNameAdapter;
    private static Orm instance;
    private static File DIR_DOWNLOADS = Environment.getExternalStoragePublicDirectory(
                                                                Environment.DIRECTORY_DOWNLOADS );
}
