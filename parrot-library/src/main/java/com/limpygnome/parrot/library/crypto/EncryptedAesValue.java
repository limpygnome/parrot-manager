package com.limpygnome.parrot.library.crypto;

import java.util.Arrays;

/**
 * Used to hold AES encrypted binary / byte array.
 */
public class EncryptedAesValue
{
    private byte[] iv;
    private byte[] value;

    public EncryptedAesValue(byte[] iv, byte[] value)
    {
        this.iv = iv;
        this.value = value;
    }

    /**
     * @return initialization vector
     */
    public byte[] getIv()
    {
        return iv;
    }

    /**
     * @return encrypted byte data
     */
    public byte[] getValue()
    {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        EncryptedAesValue that = (EncryptedAesValue) o;

        if (!Arrays.equals(iv, that.iv)) return false;
        return Arrays.equals(value, that.value);

    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(iv);
        result = 31 * result + Arrays.hashCode(value);
        return result;
    }

}
