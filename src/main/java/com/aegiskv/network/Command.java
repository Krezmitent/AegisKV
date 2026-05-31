package com.aegiskv.network;

public enum Command {
    UNKNOWN((byte) 0x00),
    PUT((byte) 0x01),
    GET((byte) 0x02),
    DELETE((byte) 0x03),
    SYNC((byte) 0x04);

    private final byte code;

    Command(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    public static Command fromCode(byte code) {
        switch (code) {
            case 0x01: return PUT;
            case 0x02: return GET;
            case 0x03: return DELETE;
            case 0x04: return SYNC;
            default: return UNKNOWN;
        }
    }
}
