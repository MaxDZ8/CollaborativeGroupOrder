// Generated by the protocol buffer compiler.  DO NOT EDIT!

package com.massimodz8.collaborativegrouporder.protocol.nano;

@SuppressWarnings("hiding")
public interface Session {

  public static final class RealWorldData extends
      com.google.protobuf.nano.MessageNano {

    // enum State
    public static final int KNOWN = 0;
    public static final int ADVENTURING = 1;
    public static final int FIGHTING = 2;

    private static volatile RealWorldData[] _emptyArray;
    public static RealWorldData[] emptyArray() {
      // Lazily initializes the empty array
      if (_emptyArray == null) {
        synchronized (
            com.google.protobuf.nano.InternalNano.LAZY_INIT_LOCK) {
          if (_emptyArray == null) {
            _emptyArray = new RealWorldData[0];
          }
        }
      }
      return _emptyArray;
    }

    // optional .google.protobuf.Timestamp lastBegin = 1;
    public com.google.protobuf.nano.Timestamp lastBegin;

    // optional .google.protobuf.Timestamp lastSaved = 2;
    public com.google.protobuf.nano.Timestamp lastSaved;

    // optional .google.protobuf.Timestamp spent = 3;
    public com.google.protobuf.nano.Timestamp spent;

    // optional uint32 numSessions = 4;
    public int numSessions;

    // optional string note = 5;
    public java.lang.String note;

    // optional .RealWorldData.State state = 6;
    public int state;

    public RealWorldData() {
      clear();
    }

    public RealWorldData clear() {
      lastBegin = null;
      lastSaved = null;
      spent = null;
      numSessions = 0;
      note = "";
      state = com.massimodz8.collaborativegrouporder.protocol.nano.Session.RealWorldData.KNOWN;
      cachedSize = -1;
      return this;
    }

    @Override
    public void writeTo(com.google.protobuf.nano.CodedOutputByteBufferNano output)
        throws java.io.IOException {
      if (this.lastBegin != null) {
        output.writeMessage(1, this.lastBegin);
      }
      if (this.lastSaved != null) {
        output.writeMessage(2, this.lastSaved);
      }
      if (this.spent != null) {
        output.writeMessage(3, this.spent);
      }
      if (this.numSessions != 0) {
        output.writeUInt32(4, this.numSessions);
      }
      if (!this.note.equals("")) {
        output.writeString(5, this.note);
      }
      if (this.state != com.massimodz8.collaborativegrouporder.protocol.nano.Session.RealWorldData.KNOWN) {
        output.writeInt32(6, this.state);
      }
      super.writeTo(output);
    }

    @Override
    protected int computeSerializedSize() {
      int size = super.computeSerializedSize();
      if (this.lastBegin != null) {
        size += com.google.protobuf.nano.CodedOutputByteBufferNano
          .computeMessageSize(1, this.lastBegin);
      }
      if (this.lastSaved != null) {
        size += com.google.protobuf.nano.CodedOutputByteBufferNano
          .computeMessageSize(2, this.lastSaved);
      }
      if (this.spent != null) {
        size += com.google.protobuf.nano.CodedOutputByteBufferNano
          .computeMessageSize(3, this.spent);
      }
      if (this.numSessions != 0) {
        size += com.google.protobuf.nano.CodedOutputByteBufferNano
            .computeUInt32Size(4, this.numSessions);
      }
      if (!this.note.equals("")) {
        size += com.google.protobuf.nano.CodedOutputByteBufferNano
            .computeStringSize(5, this.note);
      }
      if (this.state != com.massimodz8.collaborativegrouporder.protocol.nano.Session.RealWorldData.KNOWN) {
        size += com.google.protobuf.nano.CodedOutputByteBufferNano
          .computeInt32Size(6, this.state);
      }
      return size;
    }

    @Override
    public RealWorldData mergeFrom(
            com.google.protobuf.nano.CodedInputByteBufferNano input)
        throws java.io.IOException {
      while (true) {
        int tag = input.readTag();
        switch (tag) {
          case 0:
            return this;
          default: {
            if (!com.google.protobuf.nano.WireFormatNano.parseUnknownField(input, tag)) {
              return this;
            }
            break;
          }
          case 10: {
            if (this.lastBegin == null) {
              this.lastBegin = new com.google.protobuf.nano.Timestamp();
            }
            input.readMessage(this.lastBegin);
            break;
          }
          case 18: {
            if (this.lastSaved == null) {
              this.lastSaved = new com.google.protobuf.nano.Timestamp();
            }
            input.readMessage(this.lastSaved);
            break;
          }
          case 26: {
            if (this.spent == null) {
              this.spent = new com.google.protobuf.nano.Timestamp();
            }
            input.readMessage(this.spent);
            break;
          }
          case 32: {
            this.numSessions = input.readUInt32();
            break;
          }
          case 42: {
            this.note = input.readString();
            break;
          }
          case 48: {
            int value = input.readInt32();
            switch (value) {
              case com.massimodz8.collaborativegrouporder.protocol.nano.Session.RealWorldData.KNOWN:
              case com.massimodz8.collaborativegrouporder.protocol.nano.Session.RealWorldData.ADVENTURING:
              case com.massimodz8.collaborativegrouporder.protocol.nano.Session.RealWorldData.FIGHTING:
                this.state = value;
                break;
            }
            break;
          }
        }
      }
    }

    public static RealWorldData parseFrom(byte[] data)
        throws com.google.protobuf.nano.InvalidProtocolBufferNanoException {
      return com.google.protobuf.nano.MessageNano.mergeFrom(new RealWorldData(), data);
    }

    public static RealWorldData parseFrom(
            com.google.protobuf.nano.CodedInputByteBufferNano input)
        throws java.io.IOException {
      return new RealWorldData().mergeFrom(input);
    }
  }

  public static final class LiveData extends
      com.google.protobuf.nano.MessageNano {

    private static volatile LiveData[] _emptyArray;
    public static LiveData[] emptyArray() {
      // Lazily initializes the empty array
      if (_emptyArray == null) {
        synchronized (
            com.google.protobuf.nano.InternalNano.LAZY_INIT_LOCK) {
          if (_emptyArray == null) {
            _emptyArray = new LiveData[0];
          }
        }
      }
      return _emptyArray;
    }

    public LiveData() {
      clear();
    }

    public LiveData clear() {
      cachedSize = -1;
      return this;
    }

    @Override
    public LiveData mergeFrom(
            com.google.protobuf.nano.CodedInputByteBufferNano input)
        throws java.io.IOException {
      while (true) {
        int tag = input.readTag();
        switch (tag) {
          case 0:
            return this;
          default: {
            if (!com.google.protobuf.nano.WireFormatNano.parseUnknownField(input, tag)) {
              return this;
            }
            break;
          }
        }
      }
    }

    public static LiveData parseFrom(byte[] data)
        throws com.google.protobuf.nano.InvalidProtocolBufferNanoException {
      return com.google.protobuf.nano.MessageNano.mergeFrom(new LiveData(), data);
    }

    public static LiveData parseFrom(
            com.google.protobuf.nano.CodedInputByteBufferNano input)
        throws java.io.IOException {
      return new LiveData().mergeFrom(input);
    }
  }

  public static final class BattleData extends
      com.google.protobuf.nano.MessageNano {

    private static volatile BattleData[] _emptyArray;
    public static BattleData[] emptyArray() {
      // Lazily initializes the empty array
      if (_emptyArray == null) {
        synchronized (
            com.google.protobuf.nano.InternalNano.LAZY_INIT_LOCK) {
          if (_emptyArray == null) {
            _emptyArray = new BattleData[0];
          }
        }
      }
      return _emptyArray;
    }

    public BattleData() {
      clear();
    }

    public BattleData clear() {
      cachedSize = -1;
      return this;
    }

    @Override
    public BattleData mergeFrom(
            com.google.protobuf.nano.CodedInputByteBufferNano input)
        throws java.io.IOException {
      while (true) {
        int tag = input.readTag();
        switch (tag) {
          case 0:
            return this;
          default: {
            if (!com.google.protobuf.nano.WireFormatNano.parseUnknownField(input, tag)) {
              return this;
            }
            break;
          }
        }
      }
    }

    public static BattleData parseFrom(byte[] data)
        throws com.google.protobuf.nano.InvalidProtocolBufferNanoException {
      return com.google.protobuf.nano.MessageNano.mergeFrom(new BattleData(), data);
    }

    public static BattleData parseFrom(
            com.google.protobuf.nano.CodedInputByteBufferNano input)
        throws java.io.IOException {
      return new BattleData().mergeFrom(input);
    }
  }
}
