package com.devbaltasarq.corvar.core;


import android.util.JsonReader;
import android.util.JsonWriter;
import android.util.Log;
import android.util.Pair;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collection;
import java.util.ArrayList;

/** Represents the results of a given experiment. */
public class Result extends Persistent {
    private static final String LogTag = Result.class.getSimpleName();

    public static class BeatEvent extends Pair<Long, Long> {
        public BeatEvent(long time, long rr)
        {
            super( time, rr );
        }

        public long getTime()
        {
            return this.first;
        }

        public long getRR()
        {
            return this.second;
        }
    }

    public static class Builder {
        public Builder(long dateTime)
        {
            this.dateTime = dateTime;
            this.rrs = new ArrayList<>();
        }

        /** Adds a new Event to the list.
          * @param beat the new time, rr pair.
          */
        public void add(BeatEvent beat)
        {
            this.rrs.add( beat );
        }

        /** Adds all the given events.
          * @param beats a collection of time, rr pairs.
          */
        public void addAll(Collection<BeatEvent> beats)
        {
            this.rrs.addAll( beats );
        }

        /** Clears all the stored events. */
        public void clear()
        {
            this.rrs.clear();
        }

        /** @return all stored hearbeats up to this moment. */
        public BeatEvent[] getAllRRs()
        {
            return this.rrs.toArray( new BeatEvent[ 0 ] );
        }

        public Result build(long elapsedMillis)
        {
            return this.build( Tag.NO_TAG, elapsedMillis );
        }

        /** @return the appropriate Result object, given the current data.
          * @param elapsedMillis the length of the measurement
          * @param tag the tag for this measurement
          * @see Result
          */
        public Result build(Tag tag, long elapsedMillis)
        {
            return new Result(
                            tag,
                            Id.create(),
                            this.dateTime,
                            elapsedMillis,
                            this.rrs.toArray( new BeatEvent[ 0 ]) );
        }

        private long dateTime;
        private ArrayList<BeatEvent> rrs;
    }


    /** Creates a new Result, in which the events of the experiment will be stored.
     * @param tag  the tag for the result.
     * @param id   the id of the result.
     * @param dateTime the moment (in millis) this experiment was collected.
     * @param rrs the collection of time/rr's pairs.
     */
    private Result(Tag tag, Id id, long dateTime, long durationInMillis, BeatEvent[] rrs)
    {
        super( id );

        this.tag = tag;
        this.durationInMillis = durationInMillis;
        this.dateTime = dateTime;
        this.rrs = rrs;
    }

    @Override
    public TypeId getTypeId()
    {
        return TypeId.Result;
    }

    /** @return the date for this result. */
    public long getTime()
    {
        return this.dateTime;
    }

    /** @return the tag for this result. */
    public Tag getTag() { return this.tag; }

    @Override
    public int hashCode()
    {
        return ( 11 * this.getId().hashCode() )
                + Long.valueOf( 13 * this.getDurationInMillis() ).hashCode()
                + ( 17 * this.rrs.length )
                + ( 23 * this.getTag().hashCode() );
    }

    @Override
    public boolean equals(Object o)
    {
        boolean toret = false;

        if ( o instanceof Result ) {
            Result ro = (Result) o;

            if ( this.getTag().equals( ro.getTag() )
              && this.getDurationInMillis() == ro.getDurationInMillis()
              && this.rrs.length == ro.rrs.length )
            {
                toret = true;

                for(int i = 0; i < this.rrs.length; ++i) {
                    if ( this.rrs[ i ] != ro.rrs[ i ] ) {
                        toret = false;
                        break;
                    }
                }

            }
        }

        return toret;
    }

    /** @return all rr's in this result. Warning: the list can be huge. */
    public BeatEvent[] getRRsCopy()
    {
        return Arrays.copyOf( this.rrs, this.rrs.length );
    }

    /** Creates the standard pair of text files, one for heatbeats,
      * and another one to know when the activity changed.
      */
    public void exportToStdTextFormat(Writer beatsStream) throws IOException
    {
        // Run all over the rr's and scatter them on files
        for (BeatEvent rr : this.rrs) {
            beatsStream.write( Long.toString( rr.getRR() ) );
            beatsStream.write( '\n' );
        }

        return;
    }

    /** @return the number of rr's stored. */
    public int size()
    {
        return this.rrs.length;
    }

    public String getResultFileName()
    {
        return buildResultFileName( this );
    }

    /** @return the duration in millis. Will throw if the experiment is not finished yet. */
    public long getDurationInMillis()
    {
        return this.durationInMillis;
    }

    @Override
    public String toString()
    {
        return this.getId() + "@" + this.getTime() + ": " + this.getTag()
                + " - " + new Duration( this.getDurationInMillis() ).toChronoString();
    }

    @Override
    public void writeToJSON(JsonWriter jsonWriter) throws IOException
    {
        this.writeIdToJSON( jsonWriter );
        jsonWriter.name( Orm.FIELD_TAG ).value( this.getTag().toString() );
        jsonWriter.name( Orm.FIELD_DATE ).value( this.getTime() );
        jsonWriter.name( Orm.FIELD_TIME ).value( this.getDurationInMillis() );

        jsonWriter.name( Orm.FIELD_RRS ).beginArray();
        for(BeatEvent rr: this.rrs) {
            jsonWriter.beginObject();
            jsonWriter.name( Orm.FIELD_RR ).value( rr.getRR() );
            jsonWriter.name( Orm.FIELD_TIME ).value( rr.getTime() );
            jsonWriter.endObject();
        }

        jsonWriter.endArray();
    }

    public static Result fromJSON(Reader reader) throws JSONException
    {
        final JsonReader jsonReader = new JsonReader( reader );
        final ArrayList<BeatEvent> rrs = new ArrayList<>();
        Result toret;
        long durationInMillis = -1L;
        TypeId typeId = null;
        Id id = null;
        Tag tag = Tag.NO_TAG;
        long dateTime = -1L;

        // Load data
        try {
            jsonReader.beginObject();
            while ( jsonReader.hasNext() ) {
                final String nextName = jsonReader.nextName();

                if ( nextName.equals( Orm.FIELD_TAG ) ) {
                    tag = new Tag( jsonReader.nextString() );
                }
                else
                if ( nextName.equals( Orm.FIELD_DATE ) ) {
                    dateTime = jsonReader.nextLong();
                }
                else
                if ( nextName.equals( Orm.FIELD_TIME ) ) {
                    durationInMillis = jsonReader.nextLong();
                }
                else
                if ( nextName.equals( Orm.FIELD_TYPE_ID ) ) {
                    typeId = readTypeIdFromJson( jsonReader );
                }
                else
                if ( nextName.equals( Id.FIELD ) ) {
                    id = readIdFromJSON( jsonReader );
                }
                else
                if ( nextName.equals( Orm.FIELD_RRS ) ) {
                    long time = -1;
                    long rr = -1;

                    jsonReader.beginArray();

                    // Read each time, rr pair
                    while( jsonReader.hasNext() ) {
                        jsonReader.beginObject();

                        // Read the individual time, rr object.
                        while( jsonReader.hasNext() ) {
                            final String pairNextName = jsonReader.nextName();

                            if ( pairNextName.equals( Orm.FIELD_TIME ) ) {
                                time = jsonReader.nextLong();
                            }
                            else
                            if ( pairNextName.equals( Orm.FIELD_RR ) ) {
                                rr = jsonReader.nextLong();
                            }
                        }

                        jsonReader.endObject();

                        if ( time >= 0
                          && rr >= 0 )
                        {
                            rrs.add( new BeatEvent( time, rr ) );
                        } else {
                            throw new JSONException( "incomplete <time, rr> pair" );
                        }
                    }

                    jsonReader.endArray();
                }
            }
        } catch(IOException exc)
        {
            final String ERROR_MSG = "Creating result from JSON: " + exc.getMessage();

            Log.e( LogTag, ERROR_MSG );
            throw new JSONException( ERROR_MSG );
        }

        // Chk
        if ( id == null
          || dateTime < 0
          || durationInMillis < 0
          || typeId != TypeId.Result )
        {
            final String msg = "Creating result from JSON: invalid or missing data.";

            Log.e( LogTag, msg );
            throw new JSONException( msg );
        } else {
            final Orm orm = Orm.get();

            try {
                toret = new Result( tag,
                                    id,
                                    dateTime,
                                    durationInMillis,
                                    rrs.toArray( new BeatEvent[ 0 ] ) );

                orm.store( toret );
            } catch(IOException exc) {
                final String ERROR_MSG = "Retrieving results' experiment or user data set: " + exc.getMessage();

                Log.e( LogTag, ERROR_MSG );
                throw new JSONException( ERROR_MSG );
            }
        }

        return toret;
    }

    /** Creates the result name. This name contains important info.
      * @param res The result to build a name for.
      */
    static String buildResultFileName(Result res)
    {
        return TypeId.Result.toString().toLowerCase()
                + "-i" + res.getId()
                + "-g" + res.getTag().toString()
                + "-t" + res.getTime()
                + "." + Orm.getFileExtFor( TypeId.Result );
    }

    /** @return the result's time - date, reading it from its name.
     * @param resName the name of the result to extract the time from.
     */
    public static long parseTimeFromName(String resName)
    {
        final String strTime = parseName( resName )[ 3 ];

        if ( strTime.charAt( 0 ) != 't' ) {
            throw new Error( "malformed result name looking for time: "
                    + strTime
                    + "/" + resName );
        }

        return Long.parseLong( strTime.substring( 1 ) );
    }

    /** @return the result's tag, reading it from its name.
     * @param resName the name of the result to extract the time from.
     */
    public static String parseTagFromName(String resName)
    {
        final String strTag = parseName( resName )[ 2 ];

        if ( strTag.charAt( 0 ) != 'g' ) {
            throw new Error( "malformed result name looking for time: "
                    + strTag
                    + "/" + resName );
        }

        return strTag.substring( 1 );
    }

    private static String[] parseName(String resName)
    {
        if ( resName == null
          || resName.isEmpty() )
        {
            resName = "";
        }

        resName = resName.trim();

        // Remove extension
        resName = resName.substring( 0, resName.lastIndexOf( '.' ) );

        // Divide in parts
        final String[] toret = resName.split( "-" );

        if ( toret.length != 4 ) {
            throw new Error( "dividing result name in parts" );
        }

        return toret;
    }

    private Tag tag;
    private long durationInMillis;
    private long dateTime;
    private BeatEvent[] rrs;
}
