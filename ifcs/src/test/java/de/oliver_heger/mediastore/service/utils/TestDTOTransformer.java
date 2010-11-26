package de.oliver_heger.mediastore.service.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.Date;

import org.junit.Test;

/**
 * Test class for {@code DTOTransformer}.
 *
 * @author Oliver Heger
 * @version $Id: $
 */
public class TestDTOTransformer
{
    /** Constant for a test long value. */
    private static final long LONG_VALUE = 20101109221019L;

    /** Constant for a test int value. */
    private static final int INT_VALUE = 6338373;

    /** Constant for a test string value. */
    private static final String STR_VALUE = "A test string!";

    /** Constant for a test date value. */
    private static final Date DATE_VALUE = new Date(20002020L);

    /**
     * Tries to perform a copy operation if the source bean is null.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testTransformNullSource()
    {
        DTOTransformer.transform(null, new BeanB());
    }

    /**
     * Tries to perform a copy operation if the destination bean is null.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testTransformNullDest()
    {
        DTOTransformer.transform(new BeanA(), null);
    }

    /**
     * Tests a successful transformation.
     */
    @Test
    public void testTransformSuccess()
    {
        BeanA a = new BeanA();
        a.setPropertyDate(DATE_VALUE);
        a.setPropertyInt(INT_VALUE);
        a.setPropertyLong(LONG_VALUE);
        a.setPropertyString(STR_VALUE);
        a.setSpecificAProperty(this);
        BeanB b = new BeanB();
        DTOTransformer.transform(a, b);
        assertEquals("Wrong date", DATE_VALUE, b.getPropertyDate());
        assertEquals("Wrong int", INT_VALUE, b.getPropertyInt());
        assertEquals("Wrong long", LONG_VALUE, b.getPropertyLong());
        assertEquals("Wrong string", STR_VALUE, b.getPropertyString());
        assertFalse("Specific property set", b.isSpecificBProperty());
    }

    /**
     * Tests whether an invocation target exception is handled correctly during
     * a transform operation.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testTransformITEx()
    {
        BeanA a = new BeanA()
        {
            @Override
            public Date getPropertyDate()
            {
                throw new RuntimeException("Test exception!");
            };
        };
        DTOTransformer.transform(a, new BeanB());
    }

    /**
     * A simple test bean for testing whether all properties can be copied.
     */
    public static class BeanA
    {
        private long propertyLong;

        private int propertyInt;

        private String propertyString;

        private Date propertyDate;

        private Object specificAProperty;

        public long getPropertyLong()
        {
            return propertyLong;
        }

        public void setPropertyLong(long propertyLong)
        {
            this.propertyLong = propertyLong;
        }

        public int getPropertyInt()
        {
            return propertyInt;
        }

        public void setPropertyInt(int propertyInt)
        {
            this.propertyInt = propertyInt;
        }

        public String getPropertyString()
        {
            return propertyString;
        }

        public void setPropertyString(String propertyString)
        {
            this.propertyString = propertyString;
        }

        public Date getPropertyDate()
        {
            return propertyDate;
        }

        public void setPropertyDate(Date propertyDate)
        {
            this.propertyDate = propertyDate;
        }

        public Object getSpecificAProperty()
        {
            return specificAProperty;
        }

        public void setSpecificAProperty(Object specificAProperty)
        {
            this.specificAProperty = specificAProperty;
        }
    }

    /**
     * Another simple test bean.
     */
    public static class BeanB
    {
        private long propertyLong;

        private int propertyInt;

        private String propertyString;

        private Date propertyDate;

        private boolean specificBProperty;

        public long getPropertyLong()
        {
            return propertyLong;
        }

        public void setPropertyLong(long propertyLong)
        {
            this.propertyLong = propertyLong;
        }

        public int getPropertyInt()
        {
            return propertyInt;
        }

        public void setPropertyInt(int propertyInt)
        {
            this.propertyInt = propertyInt;
        }

        public String getPropertyString()
        {
            return propertyString;
        }

        public void setPropertyString(String propertyString)
        {
            this.propertyString = propertyString;
        }

        public Date getPropertyDate()
        {
            return propertyDate;
        }

        public void setPropertyDate(Date propertyDate)
        {
            this.propertyDate = propertyDate;
        }

        public boolean isSpecificBProperty()
        {
            return specificBProperty;
        }

        public void setSpecificBProperty(boolean specificBProperty)
        {
            this.specificBProperty = specificBProperty;
        }
    }
}