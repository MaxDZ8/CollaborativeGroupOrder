// Generated by the protocol buffer compiler.  DO NOT EDIT!

package com.massimodz8.collaborativegrouporder.protocol.nano;

@SuppressWarnings("hiding")
public interface PreparedEncounters {

  public static final class Collection extends
      com.google.protobuf.nano.MessageNano {

    private static volatile Collection[] _emptyArray;
    public static Collection[] emptyArray() {
      // Lazily initializes the empty array
      if (_emptyArray == null) {
        synchronized (
            com.google.protobuf.nano.InternalNano.LAZY_INIT_LOCK) {
          if (_emptyArray == null) {
            _emptyArray = new Collection[0];
          }
        }
      }
      return _emptyArray;
    }

    // repeated .Battle battles = 1;
    public com.massimodz8.collaborativegrouporder.protocol.nano.PreparedEncounters.Battle[] battles;

    public Collection() {
      clear();
    }

    public Collection clear() {
      battles = com.massimodz8.collaborativegrouporder.protocol.nano.PreparedEncounters.Battle.emptyArray();
      cachedSize = -1;
      return this;
    }

    @Override
    public void writeTo(com.google.protobuf.nano.CodedOutputByteBufferNano output)
        throws java.io.IOException {
      if (this.battles != null && this.battles.length > 0) {
        for (int i = 0; i < this.battles.length; i++) {
          com.massimodz8.collaborativegrouporder.protocol.nano.PreparedEncounters.Battle element = this.battles[i];
          if (element != null) {
            output.writeMessage(1, element);
          }
        }
      }
      super.writeTo(output);
    }

    @Override
    protected int computeSerializedSize() {
      int size = super.computeSerializedSize();
      if (this.battles != null && this.battles.length > 0) {
        for (int i = 0; i < this.battles.length; i++) {
          com.massimodz8.collaborativegrouporder.protocol.nano.PreparedEncounters.Battle element = this.battles[i];
          if (element != null) {
            size += com.google.protobuf.nano.CodedOutputByteBufferNano
              .computeMessageSize(1, element);
          }
        }
      }
      return size;
    }

    @Override
    public Collection mergeFrom(
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
            int arrayLength = com.google.protobuf.nano.WireFormatNano
                .getRepeatedFieldArrayLength(input, 10);
            int i = this.battles == null ? 0 : this.battles.length;
            com.massimodz8.collaborativegrouporder.protocol.nano.PreparedEncounters.Battle[] newArray =
                new com.massimodz8.collaborativegrouporder.protocol.nano.PreparedEncounters.Battle[i + arrayLength];
            if (i != 0) {
              java.lang.System.arraycopy(this.battles, 0, newArray, 0, i);
            }
            for (; i < newArray.length - 1; i++) {
              newArray[i] = new com.massimodz8.collaborativegrouporder.protocol.nano.PreparedEncounters.Battle();
              input.readMessage(newArray[i]);
              input.readTag();
            }
            // Last one without readTag.
            newArray[i] = new com.massimodz8.collaborativegrouporder.protocol.nano.PreparedEncounters.Battle();
            input.readMessage(newArray[i]);
            this.battles = newArray;
            break;
          }
        }
      }
    }

    public static Collection parseFrom(byte[] data)
        throws com.google.protobuf.nano.InvalidProtocolBufferNanoException {
      return com.google.protobuf.nano.MessageNano.mergeFrom(new Collection(), data);
    }

    public static Collection parseFrom(
            com.google.protobuf.nano.CodedInputByteBufferNano input)
        throws java.io.IOException {
      return new Collection().mergeFrom(input);
    }
  }

  public static final class Battle extends
      com.google.protobuf.nano.MessageNano {

    private static volatile Battle[] _emptyArray;
    public static Battle[] emptyArray() {
      // Lazily initializes the empty array
      if (_emptyArray == null) {
        synchronized (
            com.google.protobuf.nano.InternalNano.LAZY_INIT_LOCK) {
          if (_emptyArray == null) {
            _emptyArray = new Battle[0];
          }
        }
      }
      return _emptyArray;
    }

    // optional .google.protobuf.Timestamp created = 1;
    public com.google.protobuf.nano.Timestamp created;

    // optional string desc = 2;
    public java.lang.String desc;

    // repeated .ActorState actors = 3;
    public com.massimodz8.collaborativegrouporder.protocol.nano.Network.ActorState[] actors;

    public Battle() {
      clear();
    }

    public Battle clear() {
      created = null;
      desc = "";
      actors = com.massimodz8.collaborativegrouporder.protocol.nano.Network.ActorState.emptyArray();
      cachedSize = -1;
      return this;
    }

    @Override
    public void writeTo(com.google.protobuf.nano.CodedOutputByteBufferNano output)
        throws java.io.IOException {
      if (this.created != null) {
        output.writeMessage(1, this.created);
      }
      if (!this.desc.equals("")) {
        output.writeString(2, this.desc);
      }
      if (this.actors != null && this.actors.length > 0) {
        for (int i = 0; i < this.actors.length; i++) {
          com.massimodz8.collaborativegrouporder.protocol.nano.Network.ActorState element = this.actors[i];
          if (element != null) {
            output.writeMessage(3, element);
          }
        }
      }
      super.writeTo(output);
    }

    @Override
    protected int computeSerializedSize() {
      int size = super.computeSerializedSize();
      if (this.created != null) {
        size += com.google.protobuf.nano.CodedOutputByteBufferNano
          .computeMessageSize(1, this.created);
      }
      if (!this.desc.equals("")) {
        size += com.google.protobuf.nano.CodedOutputByteBufferNano
            .computeStringSize(2, this.desc);
      }
      if (this.actors != null && this.actors.length > 0) {
        for (int i = 0; i < this.actors.length; i++) {
          com.massimodz8.collaborativegrouporder.protocol.nano.Network.ActorState element = this.actors[i];
          if (element != null) {
            size += com.google.protobuf.nano.CodedOutputByteBufferNano
              .computeMessageSize(3, element);
          }
        }
      }
      return size;
    }

    @Override
    public Battle mergeFrom(
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
            if (this.created == null) {
              this.created = new com.google.protobuf.nano.Timestamp();
            }
            input.readMessage(this.created);
            break;
          }
          case 18: {
            this.desc = input.readString();
            break;
          }
          case 26: {
            int arrayLength = com.google.protobuf.nano.WireFormatNano
                .getRepeatedFieldArrayLength(input, 26);
            int i = this.actors == null ? 0 : this.actors.length;
            com.massimodz8.collaborativegrouporder.protocol.nano.Network.ActorState[] newArray =
                new com.massimodz8.collaborativegrouporder.protocol.nano.Network.ActorState[i + arrayLength];
            if (i != 0) {
              java.lang.System.arraycopy(this.actors, 0, newArray, 0, i);
            }
            for (; i < newArray.length - 1; i++) {
              newArray[i] = new com.massimodz8.collaborativegrouporder.protocol.nano.Network.ActorState();
              input.readMessage(newArray[i]);
              input.readTag();
            }
            // Last one without readTag.
            newArray[i] = new com.massimodz8.collaborativegrouporder.protocol.nano.Network.ActorState();
            input.readMessage(newArray[i]);
            this.actors = newArray;
            break;
          }
        }
      }
    }

    public static Battle parseFrom(byte[] data)
        throws com.google.protobuf.nano.InvalidProtocolBufferNanoException {
      return com.google.protobuf.nano.MessageNano.mergeFrom(new Battle(), data);
    }

    public static Battle parseFrom(
            com.google.protobuf.nano.CodedInputByteBufferNano input)
        throws java.io.IOException {
      return new Battle().mergeFrom(input);
    }
  }
}
