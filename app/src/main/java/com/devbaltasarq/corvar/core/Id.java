package com.devbaltasarq.corvar.core;


/** Gives an id and its infrastructure to any other class. */
public class Id {
    public static final String FIELD = "_id";

    /** Creates a new id. */
    public Id(long id)
    {
        this.id = id;
    }

    /** @return the id itself. */
    public long get()
    {
        return this.id;
    }

    /** @return the hashcode. */
    @Override
    public int hashCode()
    {
        return Long.valueOf( this.get() ).hashCode();
    }

    /** Determine whether this is equal to another id or not. */
    @Override
    public boolean equals(Object o)
    {
        boolean toret = false;

        if ( o instanceof Id ) {
            return this.get() == ( (Id) o ).get();
        }

        return toret;
    }

    /** Copies the id into a different object.
      * @return A new object with the same id of this one.*/
    public Id copy()
    {
        return new Id( this.id );
    }

    @Override
    public String toString()
    {
        return Long.toString( this.get() );
    }

    /** @return The next id, valid for storing. */
    public static Id create()
    {
        return new Id( System.currentTimeMillis() );
    }

    private long id;
}
