// Generated by the protocol buffer compiler.  DO NOT EDIT!

package com.massimodz8.collaborativegrouporder.protocol.nano;

@SuppressWarnings("hiding")
public interface PersistentStorage {

  public static final class PartyOwnerData extends
      com.google.protobuf.nano.MessageNano {

    private static volatile PartyOwnerData[] _emptyArray;
    public static PartyOwnerData[] emptyArray() {
      // Lazily initializes the empty array
      if (_emptyArray == null) {
        synchronized (
            com.google.protobuf.nano.InternalNano.LAZY_INIT_LOCK) {
          if (_emptyArray == null) {
            _emptyArray = new PartyOwnerData[0];
          }
        }
      }
      return _emptyArray;
    }

    // optional uint32 version = 1;
    public int version;

    // repeated .Group everything = 2;
    public com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.Group[] everything;

    public PartyOwnerData() {
      clear();
    }

    public PartyOwnerData clear() {
      version = 0;
      everything = com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.Group.emptyArray();
      cachedSize = -1;
      return this;
    }

    @Override
    public void writeTo(com.google.protobuf.nano.CodedOutputByteBufferNano output)
        throws java.io.IOException {
      if (this.version != 0) {
        output.writeUInt32(1, this.version);
      }
      if (this.everything != null && this.everything.length > 0) {
        for (int i = 0; i < this.everything.length; i++) {
          com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.Group element = this.everything[i];
          if (element != null) {
            output.writeMessage(2, element);
          }
        }
      }
      super.writeTo(output);
    }

    @Override
    protected int computeSerializedSize() {
      int size = super.computeSerializedSize();
      if (this.version != 0) {
        size += com.google.protobuf.nano.CodedOutputByteBufferNano
            .computeUInt32Size(1, this.version);
      }
      if (this.everything != null && this.everything.length > 0) {
        for (int i = 0; i < this.everything.length; i++) {
          com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.Group element = this.everything[i];
          if (element != null) {
            size += com.google.protobuf.nano.CodedOutputByteBufferNano
              .computeMessageSize(2, element);
          }
        }
      }
      return size;
    }

    @Override
    public PartyOwnerData mergeFrom(
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
            this.version = input.readUInt32();
            break;
          }
          case 18: {
            int arrayLength = com.google.protobuf.nano.WireFormatNano
                .getRepeatedFieldArrayLength(input, 18);
            int i = this.everything == null ? 0 : this.everything.length;
            com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.Group[] newArray =
                new com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.Group[i + arrayLength];
            if (i != 0) {
              java.lang.System.arraycopy(this.everything, 0, newArray, 0, i);
            }
            for (; i < newArray.length - 1; i++) {
              newArray[i] = new com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.Group();
              input.readMessage(newArray[i]);
              input.readTag();
            }
            // Last one without readTag.
            newArray[i] = new com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.Group();
            input.readMessage(newArray[i]);
            this.everything = newArray;
            break;
          }
        }
      }
    }

    public static PartyOwnerData parseFrom(byte[] data)
        throws com.google.protobuf.nano.InvalidProtocolBufferNanoException {
      return com.google.protobuf.nano.MessageNano.mergeFrom(new PartyOwnerData(), data);
    }

    public static PartyOwnerData parseFrom(
            com.google.protobuf.nano.CodedInputByteBufferNano input)
        throws java.io.IOException {
      return new PartyOwnerData().mergeFrom(input);
    }
  }

  public static final class Group extends
      com.google.protobuf.nano.MessageNano {

    public static final class Definition extends
        com.google.protobuf.nano.MessageNano {

      private static volatile Definition[] _emptyArray;
      public static Definition[] emptyArray() {
        // Lazily initializes the empty array
        if (_emptyArray == null) {
          synchronized (
              com.google.protobuf.nano.InternalNano.LAZY_INIT_LOCK) {
            if (_emptyArray == null) {
              _emptyArray = new Definition[0];
            }
          }
        }
        return _emptyArray;
      }

      // repeated .Actor party = 1;
      public com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.Actor[] party;

      // repeated .Actor npcs = 2;
      public com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.Actor[] npcs;

      public Definition() {
        clear();
      }

      public Definition clear() {
        party = com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.Actor.emptyArray();
        npcs = com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.Actor.emptyArray();
        cachedSize = -1;
        return this;
      }

      @Override
      public void writeTo(com.google.protobuf.nano.CodedOutputByteBufferNano output)
          throws java.io.IOException {
        if (this.party != null && this.party.length > 0) {
          for (int i = 0; i < this.party.length; i++) {
            com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.Actor element = this.party[i];
            if (element != null) {
              output.writeMessage(1, element);
            }
          }
        }
        if (this.npcs != null && this.npcs.length > 0) {
          for (int i = 0; i < this.npcs.length; i++) {
            com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.Actor element = this.npcs[i];
            if (element != null) {
              output.writeMessage(2, element);
            }
          }
        }
        super.writeTo(output);
      }

      @Override
      protected int computeSerializedSize() {
        int size = super.computeSerializedSize();
        if (this.party != null && this.party.length > 0) {
          for (int i = 0; i < this.party.length; i++) {
            com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.Actor element = this.party[i];
            if (element != null) {
              size += com.google.protobuf.nano.CodedOutputByteBufferNano
                .computeMessageSize(1, element);
            }
          }
        }
        if (this.npcs != null && this.npcs.length > 0) {
          for (int i = 0; i < this.npcs.length; i++) {
            com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.Actor element = this.npcs[i];
            if (element != null) {
              size += com.google.protobuf.nano.CodedOutputByteBufferNano
                .computeMessageSize(2, element);
            }
          }
        }
        return size;
      }

      @Override
      public Definition mergeFrom(
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
              int i = this.party == null ? 0 : this.party.length;
              com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.Actor[] newArray =
                  new com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.Actor[i + arrayLength];
              if (i != 0) {
                java.lang.System.arraycopy(this.party, 0, newArray, 0, i);
              }
              for (; i < newArray.length - 1; i++) {
                newArray[i] = new com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.Actor();
                input.readMessage(newArray[i]);
                input.readTag();
              }
              // Last one without readTag.
              newArray[i] = new com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.Actor();
              input.readMessage(newArray[i]);
              this.party = newArray;
              break;
            }
            case 18: {
              int arrayLength = com.google.protobuf.nano.WireFormatNano
                  .getRepeatedFieldArrayLength(input, 18);
              int i = this.npcs == null ? 0 : this.npcs.length;
              com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.Actor[] newArray =
                  new com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.Actor[i + arrayLength];
              if (i != 0) {
                java.lang.System.arraycopy(this.npcs, 0, newArray, 0, i);
              }
              for (; i < newArray.length - 1; i++) {
                newArray[i] = new com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.Actor();
                input.readMessage(newArray[i]);
                input.readTag();
              }
              // Last one without readTag.
              newArray[i] = new com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.Actor();
              input.readMessage(newArray[i]);
              this.npcs = newArray;
              break;
            }
          }
        }
      }

      public static Definition parseFrom(byte[] data)
          throws com.google.protobuf.nano.InvalidProtocolBufferNanoException {
        return com.google.protobuf.nano.MessageNano.mergeFrom(new Definition(), data);
      }

      public static Definition parseFrom(
              com.google.protobuf.nano.CodedInputByteBufferNano input)
          throws java.io.IOException {
        return new Definition().mergeFrom(input);
      }
    }

    private static volatile Group[] _emptyArray;
    public static Group[] emptyArray() {
      // Lazily initializes the empty array
      if (_emptyArray == null) {
        synchronized (
            com.google.protobuf.nano.InternalNano.LAZY_INIT_LOCK) {
          if (_emptyArray == null) {
            _emptyArray = new Group[0];
          }
        }
      }
      return _emptyArray;
    }

    // optional string name = 1;
    public java.lang.String name;

    // optional bytes salt = 2;
    public byte[] salt;

    // optional .Group.Definition usually = 3;
    public com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.Group.Definition usually;

    public Group() {
      clear();
    }

    public Group clear() {
      name = "";
      salt = com.google.protobuf.nano.WireFormatNano.EMPTY_BYTES;
      usually = null;
      cachedSize = -1;
      return this;
    }

    @Override
    public void writeTo(com.google.protobuf.nano.CodedOutputByteBufferNano output)
        throws java.io.IOException {
      if (!this.name.equals("")) {
        output.writeString(1, this.name);
      }
      if (!java.util.Arrays.equals(this.salt, com.google.protobuf.nano.WireFormatNano.EMPTY_BYTES)) {
        output.writeBytes(2, this.salt);
      }
      if (this.usually != null) {
        output.writeMessage(3, this.usually);
      }
      super.writeTo(output);
    }

    @Override
    protected int computeSerializedSize() {
      int size = super.computeSerializedSize();
      if (!this.name.equals("")) {
        size += com.google.protobuf.nano.CodedOutputByteBufferNano
            .computeStringSize(1, this.name);
      }
      if (!java.util.Arrays.equals(this.salt, com.google.protobuf.nano.WireFormatNano.EMPTY_BYTES)) {
        size += com.google.protobuf.nano.CodedOutputByteBufferNano
            .computeBytesSize(2, this.salt);
      }
      if (this.usually != null) {
        size += com.google.protobuf.nano.CodedOutputByteBufferNano
          .computeMessageSize(3, this.usually);
      }
      return size;
    }

    @Override
    public Group mergeFrom(
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
            this.name = input.readString();
            break;
          }
          case 18: {
            this.salt = input.readBytes();
            break;
          }
          case 26: {
            if (this.usually == null) {
              this.usually = new com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.Group.Definition();
            }
            input.readMessage(this.usually);
            break;
          }
        }
      }
    }

    public static Group parseFrom(byte[] data)
        throws com.google.protobuf.nano.InvalidProtocolBufferNanoException {
      return com.google.protobuf.nano.MessageNano.mergeFrom(new Group(), data);
    }

    public static Group parseFrom(
            com.google.protobuf.nano.CodedInputByteBufferNano input)
        throws java.io.IOException {
      return new Group().mergeFrom(input);
    }
  }

  public static final class Actor extends
      com.google.protobuf.nano.MessageNano {

    private static volatile Actor[] _emptyArray;
    public static Actor[] emptyArray() {
      // Lazily initializes the empty array
      if (_emptyArray == null) {
        synchronized (
            com.google.protobuf.nano.InternalNano.LAZY_INIT_LOCK) {
          if (_emptyArray == null) {
            _emptyArray = new Actor[0];
          }
        }
      }
      return _emptyArray;
    }

    // optional string name = 1;
    public java.lang.String name;

    // optional uint32 level = 2;
    public int level;

    // repeated .ActorStatistics stats = 3;
    public com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.ActorStatistics[] stats;

    // optional uint32 initiative = 4;
    public int initiative;

    // optional string preparedAction = 5;
    public java.lang.String preparedAction;

    // optional uint32 temporaryHealth = 6;
    public int temporaryHealth;

    // optional uint32 healthMalus = 7;
    public int healthMalus;

    public Actor() {
      clear();
    }

    public Actor clear() {
      name = "";
      level = 0;
      stats = com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.ActorStatistics.emptyArray();
      initiative = 0;
      preparedAction = "";
      temporaryHealth = 0;
      healthMalus = 0;
      cachedSize = -1;
      return this;
    }

    @Override
    public void writeTo(com.google.protobuf.nano.CodedOutputByteBufferNano output)
        throws java.io.IOException {
      if (!this.name.equals("")) {
        output.writeString(1, this.name);
      }
      if (this.level != 0) {
        output.writeUInt32(2, this.level);
      }
      if (this.stats != null && this.stats.length > 0) {
        for (int i = 0; i < this.stats.length; i++) {
          com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.ActorStatistics element = this.stats[i];
          if (element != null) {
            output.writeMessage(3, element);
          }
        }
      }
      if (this.initiative != 0) {
        output.writeUInt32(4, this.initiative);
      }
      if (!this.preparedAction.equals("")) {
        output.writeString(5, this.preparedAction);
      }
      if (this.temporaryHealth != 0) {
        output.writeUInt32(6, this.temporaryHealth);
      }
      if (this.healthMalus != 0) {
        output.writeUInt32(7, this.healthMalus);
      }
      super.writeTo(output);
    }

    @Override
    protected int computeSerializedSize() {
      int size = super.computeSerializedSize();
      if (!this.name.equals("")) {
        size += com.google.protobuf.nano.CodedOutputByteBufferNano
            .computeStringSize(1, this.name);
      }
      if (this.level != 0) {
        size += com.google.protobuf.nano.CodedOutputByteBufferNano
            .computeUInt32Size(2, this.level);
      }
      if (this.stats != null && this.stats.length > 0) {
        for (int i = 0; i < this.stats.length; i++) {
          com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.ActorStatistics element = this.stats[i];
          if (element != null) {
            size += com.google.protobuf.nano.CodedOutputByteBufferNano
              .computeMessageSize(3, element);
          }
        }
      }
      if (this.initiative != 0) {
        size += com.google.protobuf.nano.CodedOutputByteBufferNano
            .computeUInt32Size(4, this.initiative);
      }
      if (!this.preparedAction.equals("")) {
        size += com.google.protobuf.nano.CodedOutputByteBufferNano
            .computeStringSize(5, this.preparedAction);
      }
      if (this.temporaryHealth != 0) {
        size += com.google.protobuf.nano.CodedOutputByteBufferNano
            .computeUInt32Size(6, this.temporaryHealth);
      }
      if (this.healthMalus != 0) {
        size += com.google.protobuf.nano.CodedOutputByteBufferNano
            .computeUInt32Size(7, this.healthMalus);
      }
      return size;
    }

    @Override
    public Actor mergeFrom(
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
            this.name = input.readString();
            break;
          }
          case 16: {
            this.level = input.readUInt32();
            break;
          }
          case 26: {
            int arrayLength = com.google.protobuf.nano.WireFormatNano
                .getRepeatedFieldArrayLength(input, 26);
            int i = this.stats == null ? 0 : this.stats.length;
            com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.ActorStatistics[] newArray =
                new com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.ActorStatistics[i + arrayLength];
            if (i != 0) {
              java.lang.System.arraycopy(this.stats, 0, newArray, 0, i);
            }
            for (; i < newArray.length - 1; i++) {
              newArray[i] = new com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.ActorStatistics();
              input.readMessage(newArray[i]);
              input.readTag();
            }
            // Last one without readTag.
            newArray[i] = new com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.ActorStatistics();
            input.readMessage(newArray[i]);
            this.stats = newArray;
            break;
          }
          case 32: {
            this.initiative = input.readUInt32();
            break;
          }
          case 42: {
            this.preparedAction = input.readString();
            break;
          }
          case 48: {
            this.temporaryHealth = input.readUInt32();
            break;
          }
          case 56: {
            this.healthMalus = input.readUInt32();
            break;
          }
        }
      }
    }

    public static Actor parseFrom(byte[] data)
        throws com.google.protobuf.nano.InvalidProtocolBufferNanoException {
      return com.google.protobuf.nano.MessageNano.mergeFrom(new Actor(), data);
    }

    public static Actor parseFrom(
            com.google.protobuf.nano.CodedInputByteBufferNano input)
        throws java.io.IOException {
      return new Actor().mergeFrom(input);
    }
  }

  public static final class ActorStatistics extends
      com.google.protobuf.nano.MessageNano {

    private static volatile ActorStatistics[] _emptyArray;
    public static ActorStatistics[] emptyArray() {
      // Lazily initializes the empty array
      if (_emptyArray == null) {
        synchronized (
            com.google.protobuf.nano.InternalNano.LAZY_INIT_LOCK) {
          if (_emptyArray == null) {
            _emptyArray = new ActorStatistics[0];
          }
        }
      }
      return _emptyArray;
    }

    // optional uint32 experience = 1;
    public int experience;

    // optional uint32 initBonus = 2;
    public int initBonus;

    // optional int32 healthPoints = 3;
    public int healthPoints;

    public ActorStatistics() {
      clear();
    }

    public ActorStatistics clear() {
      experience = 0;
      initBonus = 0;
      healthPoints = 0;
      cachedSize = -1;
      return this;
    }

    @Override
    public void writeTo(com.google.protobuf.nano.CodedOutputByteBufferNano output)
        throws java.io.IOException {
      if (this.experience != 0) {
        output.writeUInt32(1, this.experience);
      }
      if (this.initBonus != 0) {
        output.writeUInt32(2, this.initBonus);
      }
      if (this.healthPoints != 0) {
        output.writeInt32(3, this.healthPoints);
      }
      super.writeTo(output);
    }

    @Override
    protected int computeSerializedSize() {
      int size = super.computeSerializedSize();
      if (this.experience != 0) {
        size += com.google.protobuf.nano.CodedOutputByteBufferNano
            .computeUInt32Size(1, this.experience);
      }
      if (this.initBonus != 0) {
        size += com.google.protobuf.nano.CodedOutputByteBufferNano
            .computeUInt32Size(2, this.initBonus);
      }
      if (this.healthPoints != 0) {
        size += com.google.protobuf.nano.CodedOutputByteBufferNano
            .computeInt32Size(3, this.healthPoints);
      }
      return size;
    }

    @Override
    public ActorStatistics mergeFrom(
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
            this.experience = input.readUInt32();
            break;
          }
          case 16: {
            this.initBonus = input.readUInt32();
            break;
          }
          case 24: {
            this.healthPoints = input.readInt32();
            break;
          }
        }
      }
    }

    public static ActorStatistics parseFrom(byte[] data)
        throws com.google.protobuf.nano.InvalidProtocolBufferNanoException {
      return com.google.protobuf.nano.MessageNano.mergeFrom(new ActorStatistics(), data);
    }

    public static ActorStatistics parseFrom(
            com.google.protobuf.nano.CodedInputByteBufferNano input)
        throws java.io.IOException {
      return new ActorStatistics().mergeFrom(input);
    }
  }
}
