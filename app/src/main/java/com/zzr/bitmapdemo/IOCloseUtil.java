package com.zzr.bitmapdemo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;

public class IOCloseUtil {
    public static void close(InputStream in, OutputStream out) {

        try {
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static void close(InputStream in) {
        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void close(OutputStream out) {
        try {
            if (out != null) {
                out.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void close(Writer writer, Reader reader) {
        try {
            if (writer != null) {
                writer.close();
            }
            if (reader != null) {
                reader.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void close(Reader reader, Writer writer) {
        try {
            if (reader != null) {
                reader.close();
            }
            if (writer != null) {
                writer.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void close(Reader... readers) {
        if (readers != null && readers.length != 0) {
            try {
                for (Reader reader : readers) {
                    if (reader != null)
                        reader.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void close(Writer... writers) {
        if (writers != null && writers.length != 0) {
            try {
                for (Writer writer : writers) {
                    if (writer != null)
                        writer.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    public static void close(InputStream... inputs) {
        if (inputs != null && inputs.length != 0) {
            try {
                for (InputStream in : inputs) {
                    if (in != null)
                        in.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void close(OutputStream... outputs) {
        if (outputs != null && outputs.length != 0) {
            try {
                for (OutputStream out : outputs) {
                    if (out != null)
                        out.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
