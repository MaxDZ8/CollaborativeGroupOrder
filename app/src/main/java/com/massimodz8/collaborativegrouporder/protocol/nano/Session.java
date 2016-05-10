// Generated by the protocol buffer compiler.  DO NOT EDIT!

package com.massimodz8.collaborativegrouporder.protocol.nano;

@SuppressWarnings("hiding")
public interface Session {

  public static final class RealWorldData extends
      com.google.protobuf.nano.MessageNano {

    // enum Extra
    public static final int E_ACTOR_STATE = 0;
    public static final int E_NOT_FIGHTING = 1;
    public static final int E_BATTLE_STATE = 2;

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

    // repeated .RealWorldData.Extra types = 6;
    public int[] types;

    public RealWorldData() {
      clear();
    }

    public RealWorldData clear() {
      lastBegin = null;
      lastSaved = null;
      spent = null;
      numSessions = 0;
      note = "";
      types = com.google.protobuf.nano.WireFormatNano.EMPTY_INT_ARRAY;
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
      if (this.types != null && this.types.length > 0) {
        for (int i = 0; i < this.types.length; i++) {
          output.writeInt32(6, this.types[i]);
        }
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
      if (this.types != null && this.types.length > 0) {
        int dataSize = 0;
        for (int i = 0; i < this.types.length; i++) {
          int element = this.types[i];
          dataSize += com.google.protobuf.nano.CodedOutputByteBufferNano
              .computeInt32SizeNoTag(element);
        }
        size += dataSize;
        size += 1 * this.types.length;
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
            int length = com.google.protobuf.nano.WireFormatNano
                .getRepeatedFieldArrayLength(input, 48);
            int[] validValues = new int[length];
            int validCount = 0;
            for (int i = 0; i < length; i++) {
              if (i != 0) { // tag for first value already consumed.
                input.readTag();
              }
              int value = input.readInt32();
              switch (value) {
                case com.massimodz8.collaborativegrouporder.protocol.nano.Session.RealWorldData.E_ACTOR_STATE:
                case com.massimodz8.collaborativegrouporder.protocol.nano.Session.RealWorldData.E_NOT_FIGHTING:
                case com.massimodz8.collaborativegrouporder.protocol.nano.Session.RealWorldData.E_BATTLE_STATE:
                  validValues[validCount++] = value;
                  break;
              }
            }
            if (validCount != 0) {
              int i = this.types == null ? 0 : this.types.length;
              if (i == 0 && validCount == validValues.length) {
                this.types = validValues;
              } else {
                int[] newArray = new int[i + validCount];
                if (i != 0) {
                  java.lang.System.arraycopy(this.types, 0, newArray, 0, i);
                }
                java.lang.System.arraycopy(validValues, 0, newArray, i, validCount);
                this.types = newArray;
              }
            }
            break;
          }
          case 50: {
            int bytes = input.readRawVarint32();
            int limit = input.pushLimit(bytes);
            // First pass to compute array length.
            int arrayLength = 0;
            int startPos = input.getPosition();
            while (input.getBytesUntilLimit() > 0) {
              switch (input.readInt32()) {
                case com.massimodz8.collaborativegrouporder.protocol.nano.Session.RealWorldData.E_ACTOR_STATE:
                case com.massimodz8.collaborativegrouporder.protocol.nano.Session.RealWorldData.E_NOT_FIGHTING:
                case com.massimodz8.collaborativegrouporder.protocol.nano.Session.RealWorldData.E_BATTLE_STATE:
                  arrayLength++;
                  break;
              }
            }
            if (arrayLength != 0) {
              input.rewindToPosition(startPos);
              int i = this.types == null ? 0 : this.types.length;
              int[] newArray = new int[i + arrayLength];
              if (i != 0) {
                java.lang.System.arraycopy(this.types, 0, newArray, 0, i);
              }
              while (input.getBytesUntilLimit() > 0) {
                int value = input.readInt32();
                switch (value) {
                  case com.massimodz8.collaborativegrouporder.protocol.nano.Session.RealWorldData.E_ACTOR_STATE:
                  case com.massimodz8.collaborativegrouporder.protocol.nano.Session.RealWorldData.E_NOT_FIGHTING:
                  case com.massimodz8.collaborativegrouporder.protocol.nano.Session.RealWorldData.E_BATTLE_STATE:
                    newArray[i++] = value;
                    break;
                }
              }
              this.types = newArray;
            }
            input.popLimit(limit);
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

  public static final class NotFighting extends
      com.google.protobuf.nano.MessageNano {

    private static volatile NotFighting[] _emptyArray;
    public static NotFighting[] emptyArray() {
      // Lazily initializes the empty array
      if (_emptyArray == null) {
        synchronized (
            com.google.protobuf.nano.InternalNano.LAZY_INIT_LOCK) {
          if (_emptyArray == null) {
            _emptyArray = new NotFighting[0];
          }
        }
      }
      return _emptyArray;
    }

    // repeated uint32 peerKey = 1 [packed = true];
    public int[] peerKey;

    public NotFighting() {
      clear();
    }

    public NotFighting clear() {
      peerKey = com.google.protobuf.nano.WireFormatNano.EMPTY_INT_ARRAY;
      cachedSize = -1;
      return this;
    }

    @Override
    public void writeTo(com.google.protobuf.nano.CodedOutputByteBufferNano output)
        throws java.io.IOException {
      if (this.peerKey != null && this.peerKey.length > 0) {
        int dataSize = 0;
        for (int i = 0; i < this.peerKey.length; i++) {
          int element = this.peerKey[i];
          dataSize += com.google.protobuf.nano.CodedOutputByteBufferNano
              .computeUInt32SizeNoTag(element);
        }
        output.writeRawVarint32(10);
        output.writeRawVarint32(dataSize);
        for (int i = 0; i < this.peerKey.length; i++) {
          output.writeUInt32NoTag(this.peerKey[i]);
        }
      }
      super.writeTo(output);
    }

    @Override
    protected int computeSerializedSize() {
      int size = super.computeSerializedSize();
      if (this.peerKey != null && this.peerKey.length > 0) {
        int dataSize = 0;
        for (int i = 0; i < this.peerKey.length; i++) {
          int element = this.peerKey[i];
          dataSize += com.google.protobuf.nano.CodedOutputByteBufferNano
              .computeUInt32SizeNoTag(element);
        }
        size += dataSize;
        size += 1;
        size += com.google.protobuf.nano.CodedOutputByteBufferNano
            .computeRawVarint32Size(dataSize);
      }
      return size;
    }

    @Override
    public NotFighting mergeFrom(
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
          case 8: {
            int arrayLength = com.google.protobuf.nano.WireFormatNano
                .getRepeatedFieldArrayLength(input, 8);
            int i = this.peerKey == null ? 0 : this.peerKey.length;
            int[] newArray = new int[i + arrayLength];
            if (i != 0) {
              java.lang.System.arraycopy(this.peerKey, 0, newArray, 0, i);
            }
            for (; i < newArray.length - 1; i++) {
              newArray[i] = input.readUInt32();
              input.readTag();
            }
            // Last one without readTag.
            newArray[i] = input.readUInt32();
            this.peerKey = newArray;
            break;
          }
          case 10: {
            int length = input.readRawVarint32();
            int limit = input.pushLimit(length);
            // First pass to compute array length.
            int arrayLength = 0;
            int startPos = input.getPosition();
            while (input.getBytesUntilLimit() > 0) {
              input.readUInt32();
              arrayLength++;
            }
            input.rewindToPosition(startPos);
            int i = this.peerKey == null ? 0 : this.peerKey.length;
            int[] newArray = new int[i + arrayLength];
            if (i != 0) {
              java.lang.System.arraycopy(this.peerKey, 0, newArray, 0, i);
            }
            for (; i < newArray.length; i++) {
              newArray[i] = input.readUInt32();
            }
            this.peerKey = newArray;
            input.popLimit(limit);
            break;
          }
        }
      }
    }

    public static NotFighting parseFrom(byte[] data)
        throws com.google.protobuf.nano.InvalidProtocolBufferNanoException {
      return com.google.protobuf.nano.MessageNano.mergeFrom(new NotFighting(), data);
    }

    public static NotFighting parseFrom(
            com.google.protobuf.nano.CodedInputByteBufferNano input)
        throws java.io.IOException {
      return new NotFighting().mergeFrom(input);
    }
  }

  public static final class BattleState extends
      com.google.protobuf.nano.MessageNano {

    private static volatile BattleState[] _emptyArray;
    public static BattleState[] emptyArray() {
      // Lazily initializes the empty array
      if (_emptyArray == null) {
        synchronized (
            com.google.protobuf.nano.InternalNano.LAZY_INIT_LOCK) {
          if (_emptyArray == null) {
            _emptyArray = new BattleState[0];
          }
        }
      }
      return _emptyArray;
    }

    // optional uint32 round = 1;
    public int round;

    // optional uint32 currentActor = 2;
    public int currentActor;

    // optional bool prevWasReadied = 3;
    public boolean prevWasReadied;

    // repeated uint32 interrupted = 4 [packed = true];
    public int[] interrupted;

    // repeated int32 initiative = 5 [packed = true];
    public int[] initiative;

    // repeated uint32 id = 6 [packed = true];
    public int[] id;

    // repeated bool enabled = 7 [packed = true];
    public boolean[] enabled;

    public BattleState() {
      clear();
    }

    public BattleState clear() {
      round = 0;
      currentActor = 0;
      prevWasReadied = false;
      interrupted = com.google.protobuf.nano.WireFormatNano.EMPTY_INT_ARRAY;
      initiative = com.google.protobuf.nano.WireFormatNano.EMPTY_INT_ARRAY;
      id = com.google.protobuf.nano.WireFormatNano.EMPTY_INT_ARRAY;
      enabled = com.google.protobuf.nano.WireFormatNano.EMPTY_BOOLEAN_ARRAY;
      cachedSize = -1;
      return this;
    }

    @Override
    public void writeTo(com.google.protobuf.nano.CodedOutputByteBufferNano output)
        throws java.io.IOException {
      if (this.round != 0) {
        output.writeUInt32(1, this.round);
      }
      if (this.currentActor != 0) {
        output.writeUInt32(2, this.currentActor);
      }
      if (this.prevWasReadied != false) {
        output.writeBool(3, this.prevWasReadied);
      }
      if (this.interrupted != null && this.interrupted.length > 0) {
        int dataSize = 0;
        for (int i = 0; i < this.interrupted.length; i++) {
          int element = this.interrupted[i];
          dataSize += com.google.protobuf.nano.CodedOutputByteBufferNano
              .computeUInt32SizeNoTag(element);
        }
        output.writeRawVarint32(34);
        output.writeRawVarint32(dataSize);
        for (int i = 0; i < this.interrupted.length; i++) {
          output.writeUInt32NoTag(this.interrupted[i]);
        }
      }
      if (this.initiative != null && this.initiative.length > 0) {
        int dataSize = 0;
        for (int i = 0; i < this.initiative.length; i++) {
          int element = this.initiative[i];
          dataSize += com.google.protobuf.nano.CodedOutputByteBufferNano
              .computeInt32SizeNoTag(element);
        }
        output.writeRawVarint32(42);
        output.writeRawVarint32(dataSize);
        for (int i = 0; i < this.initiative.length; i++) {
          output.writeInt32NoTag(this.initiative[i]);
        }
      }
      if (this.id != null && this.id.length > 0) {
        int dataSize = 0;
        for (int i = 0; i < this.id.length; i++) {
          int element = this.id[i];
          dataSize += com.google.protobuf.nano.CodedOutputByteBufferNano
              .computeUInt32SizeNoTag(element);
        }
        output.writeRawVarint32(50);
        output.writeRawVarint32(dataSize);
        for (int i = 0; i < this.id.length; i++) {
          output.writeUInt32NoTag(this.id[i]);
        }
      }
      if (this.enabled != null && this.enabled.length > 0) {
        int dataSize = 1 * this.enabled.length;
        output.writeRawVarint32(58);
        output.writeRawVarint32(dataSize);
        for (int i = 0; i < this.enabled.length; i++) {
          output.writeBoolNoTag(this.enabled[i]);
        }
      }
      super.writeTo(output);
    }

    @Override
    protected int computeSerializedSize() {
      int size = super.computeSerializedSize();
      if (this.round != 0) {
        size += com.google.protobuf.nano.CodedOutputByteBufferNano
            .computeUInt32Size(1, this.round);
      }
      if (this.currentActor != 0) {
        size += com.google.protobuf.nano.CodedOutputByteBufferNano
            .computeUInt32Size(2, this.currentActor);
      }
      if (this.prevWasReadied != false) {
        size += com.google.protobuf.nano.CodedOutputByteBufferNano
            .computeBoolSize(3, this.prevWasReadied);
      }
      if (this.interrupted != null && this.interrupted.length > 0) {
        int dataSize = 0;
        for (int i = 0; i < this.interrupted.length; i++) {
          int element = this.interrupted[i];
          dataSize += com.google.protobuf.nano.CodedOutputByteBufferNano
              .computeUInt32SizeNoTag(element);
        }
        size += dataSize;
        size += 1;
        size += com.google.protobuf.nano.CodedOutputByteBufferNano
            .computeRawVarint32Size(dataSize);
      }
      if (this.initiative != null && this.initiative.length > 0) {
        int dataSize = 0;
        for (int i = 0; i < this.initiative.length; i++) {
          int element = this.initiative[i];
          dataSize += com.google.protobuf.nano.CodedOutputByteBufferNano
              .computeInt32SizeNoTag(element);
        }
        size += dataSize;
        size += 1;
        size += com.google.protobuf.nano.CodedOutputByteBufferNano
            .computeRawVarint32Size(dataSize);
      }
      if (this.id != null && this.id.length > 0) {
        int dataSize = 0;
        for (int i = 0; i < this.id.length; i++) {
          int element = this.id[i];
          dataSize += com.google.protobuf.nano.CodedOutputByteBufferNano
              .computeUInt32SizeNoTag(element);
        }
        size += dataSize;
        size += 1;
        size += com.google.protobuf.nano.CodedOutputByteBufferNano
            .computeRawVarint32Size(dataSize);
      }
      if (this.enabled != null && this.enabled.length > 0) {
        int dataSize = 1 * this.enabled.length;
        size += dataSize;
        size += 1;
        size += com.google.protobuf.nano.CodedOutputByteBufferNano
            .computeRawVarint32Size(dataSize);
      }
      return size;
    }

    @Override
    public BattleState mergeFrom(
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
          case 8: {
            this.round = input.readUInt32();
            break;
          }
          case 16: {
            this.currentActor = input.readUInt32();
            break;
          }
          case 24: {
            this.prevWasReadied = input.readBool();
            break;
          }
          case 32: {
            int arrayLength = com.google.protobuf.nano.WireFormatNano
                .getRepeatedFieldArrayLength(input, 32);
            int i = this.interrupted == null ? 0 : this.interrupted.length;
            int[] newArray = new int[i + arrayLength];
            if (i != 0) {
              java.lang.System.arraycopy(this.interrupted, 0, newArray, 0, i);
            }
            for (; i < newArray.length - 1; i++) {
              newArray[i] = input.readUInt32();
              input.readTag();
            }
            // Last one without readTag.
            newArray[i] = input.readUInt32();
            this.interrupted = newArray;
            break;
          }
          case 34: {
            int length = input.readRawVarint32();
            int limit = input.pushLimit(length);
            // First pass to compute array length.
            int arrayLength = 0;
            int startPos = input.getPosition();
            while (input.getBytesUntilLimit() > 0) {
              input.readUInt32();
              arrayLength++;
            }
            input.rewindToPosition(startPos);
            int i = this.interrupted == null ? 0 : this.interrupted.length;
            int[] newArray = new int[i + arrayLength];
            if (i != 0) {
              java.lang.System.arraycopy(this.interrupted, 0, newArray, 0, i);
            }
            for (; i < newArray.length; i++) {
              newArray[i] = input.readUInt32();
            }
            this.interrupted = newArray;
            input.popLimit(limit);
            break;
          }
          case 40: {
            int arrayLength = com.google.protobuf.nano.WireFormatNano
                .getRepeatedFieldArrayLength(input, 40);
            int i = this.initiative == null ? 0 : this.initiative.length;
            int[] newArray = new int[i + arrayLength];
            if (i != 0) {
              java.lang.System.arraycopy(this.initiative, 0, newArray, 0, i);
            }
            for (; i < newArray.length - 1; i++) {
              newArray[i] = input.readInt32();
              input.readTag();
            }
            // Last one without readTag.
            newArray[i] = input.readInt32();
            this.initiative = newArray;
            break;
          }
          case 42: {
            int length = input.readRawVarint32();
            int limit = input.pushLimit(length);
            // First pass to compute array length.
            int arrayLength = 0;
            int startPos = input.getPosition();
            while (input.getBytesUntilLimit() > 0) {
              input.readInt32();
              arrayLength++;
            }
            input.rewindToPosition(startPos);
            int i = this.initiative == null ? 0 : this.initiative.length;
            int[] newArray = new int[i + arrayLength];
            if (i != 0) {
              java.lang.System.arraycopy(this.initiative, 0, newArray, 0, i);
            }
            for (; i < newArray.length; i++) {
              newArray[i] = input.readInt32();
            }
            this.initiative = newArray;
            input.popLimit(limit);
            break;
          }
          case 48: {
            int arrayLength = com.google.protobuf.nano.WireFormatNano
                .getRepeatedFieldArrayLength(input, 48);
            int i = this.id == null ? 0 : this.id.length;
            int[] newArray = new int[i + arrayLength];
            if (i != 0) {
              java.lang.System.arraycopy(this.id, 0, newArray, 0, i);
            }
            for (; i < newArray.length - 1; i++) {
              newArray[i] = input.readUInt32();
              input.readTag();
            }
            // Last one without readTag.
            newArray[i] = input.readUInt32();
            this.id = newArray;
            break;
          }
          case 50: {
            int length = input.readRawVarint32();
            int limit = input.pushLimit(length);
            // First pass to compute array length.
            int arrayLength = 0;
            int startPos = input.getPosition();
            while (input.getBytesUntilLimit() > 0) {
              input.readUInt32();
              arrayLength++;
            }
            input.rewindToPosition(startPos);
            int i = this.id == null ? 0 : this.id.length;
            int[] newArray = new int[i + arrayLength];
            if (i != 0) {
              java.lang.System.arraycopy(this.id, 0, newArray, 0, i);
            }
            for (; i < newArray.length; i++) {
              newArray[i] = input.readUInt32();
            }
            this.id = newArray;
            input.popLimit(limit);
            break;
          }
          case 56: {
            int arrayLength = com.google.protobuf.nano.WireFormatNano
                .getRepeatedFieldArrayLength(input, 56);
            int i = this.enabled == null ? 0 : this.enabled.length;
            boolean[] newArray = new boolean[i + arrayLength];
            if (i != 0) {
              java.lang.System.arraycopy(this.enabled, 0, newArray, 0, i);
            }
            for (; i < newArray.length - 1; i++) {
              newArray[i] = input.readBool();
              input.readTag();
            }
            // Last one without readTag.
            newArray[i] = input.readBool();
            this.enabled = newArray;
            break;
          }
          case 58: {
            int length = input.readRawVarint32();
            int limit = input.pushLimit(length);
            // First pass to compute array length.
            int arrayLength = 0;
            int startPos = input.getPosition();
            while (input.getBytesUntilLimit() > 0) {
              input.readBool();
              arrayLength++;
            }
            input.rewindToPosition(startPos);
            int i = this.enabled == null ? 0 : this.enabled.length;
            boolean[] newArray = new boolean[i + arrayLength];
            if (i != 0) {
              java.lang.System.arraycopy(this.enabled, 0, newArray, 0, i);
            }
            for (; i < newArray.length; i++) {
              newArray[i] = input.readBool();
            }
            this.enabled = newArray;
            input.popLimit(limit);
            break;
          }
        }
      }
    }

    public static BattleState parseFrom(byte[] data)
        throws com.google.protobuf.nano.InvalidProtocolBufferNanoException {
      return com.google.protobuf.nano.MessageNano.mergeFrom(new BattleState(), data);
    }

    public static BattleState parseFrom(
            com.google.protobuf.nano.CodedInputByteBufferNano input)
        throws java.io.IOException {
      return new BattleState().mergeFrom(input);
    }
  }
}
