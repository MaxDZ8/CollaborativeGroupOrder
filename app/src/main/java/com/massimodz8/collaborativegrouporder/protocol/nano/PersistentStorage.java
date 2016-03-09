// Generated by the protocol buffer compiler.  DO NOT EDIT!

package com.massimodz8.collaborativegrouporder.protocol.nano;

@SuppressWarnings("hiding")
public interface PersistentStorage {

  public static final class PartyOwnerData extends
      com.google.protobuf.nano.MessageNano {

    public static final class DeviceInfo extends
        com.google.protobuf.nano.MessageNano {

      private static volatile DeviceInfo[] _emptyArray;
      public static DeviceInfo[] emptyArray() {
        // Lazily initializes the empty array
        if (_emptyArray == null) {
          synchronized (
              com.google.protobuf.nano.InternalNano.LAZY_INIT_LOCK) {
            if (_emptyArray == null) {
              _emptyArray = new DeviceInfo[0];
            }
          }
        }
        return _emptyArray;
      }

      // optional bytes salt = 1;
      public byte[] salt;

      // optional string name = 2;
      public java.lang.String name;

      // optional string avatarFile = 3;
      public java.lang.String avatarFile;

      public DeviceInfo() {
        clear();
      }

      public DeviceInfo clear() {
        salt = com.google.protobuf.nano.WireFormatNano.EMPTY_BYTES;
        name = "";
        avatarFile = "";
        cachedSize = -1;
        return this;
      }

      @Override
      public void writeTo(com.google.protobuf.nano.CodedOutputByteBufferNano output)
          throws java.io.IOException {
        if (!java.util.Arrays.equals(this.salt, com.google.protobuf.nano.WireFormatNano.EMPTY_BYTES)) {
          output.writeBytes(1, this.salt);
        }
        if (!this.name.equals("")) {
          output.writeString(2, this.name);
        }
        if (!this.avatarFile.equals("")) {
          output.writeString(3, this.avatarFile);
        }
        super.writeTo(output);
      }

      @Override
      protected int computeSerializedSize() {
        int size = super.computeSerializedSize();
        if (!java.util.Arrays.equals(this.salt, com.google.protobuf.nano.WireFormatNano.EMPTY_BYTES)) {
          size += com.google.protobuf.nano.CodedOutputByteBufferNano
              .computeBytesSize(1, this.salt);
        }
        if (!this.name.equals("")) {
          size += com.google.protobuf.nano.CodedOutputByteBufferNano
              .computeStringSize(2, this.name);
        }
        if (!this.avatarFile.equals("")) {
          size += com.google.protobuf.nano.CodedOutputByteBufferNano
              .computeStringSize(3, this.avatarFile);
        }
        return size;
      }

      @Override
      public DeviceInfo mergeFrom(
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
              this.salt = input.readBytes();
              break;
            }
            case 18: {
              this.name = input.readString();
              break;
            }
            case 26: {
              this.avatarFile = input.readString();
              break;
            }
          }
        }
      }

      public static DeviceInfo parseFrom(byte[] data)
          throws com.google.protobuf.nano.InvalidProtocolBufferNanoException {
        return com.google.protobuf.nano.MessageNano.mergeFrom(new DeviceInfo(), data);
      }

      public static DeviceInfo parseFrom(
              com.google.protobuf.nano.CodedInputByteBufferNano input)
          throws java.io.IOException {
        return new DeviceInfo().mergeFrom(input);
      }
    }

    public static final class Group extends
        com.google.protobuf.nano.MessageNano {

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

      // repeated .PartyOwnerData.DeviceInfo devices = 2;
      public com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.PartyOwnerData.DeviceInfo[] devices;

      // repeated .ActorDefinition party = 3;
      public com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.ActorDefinition[] party;

      // repeated .ActorDefinition npcs = 4;
      public com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.ActorDefinition[] npcs;

      // optional string imageFile = 5;
      public java.lang.String imageFile;

      public Group() {
        clear();
      }

      public Group clear() {
        name = "";
        devices = com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.PartyOwnerData.DeviceInfo.emptyArray();
        party = com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.ActorDefinition.emptyArray();
        npcs = com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.ActorDefinition.emptyArray();
        imageFile = "";
        cachedSize = -1;
        return this;
      }

      @Override
      public void writeTo(com.google.protobuf.nano.CodedOutputByteBufferNano output)
          throws java.io.IOException {
        if (!this.name.equals("")) {
          output.writeString(1, this.name);
        }
        if (this.devices != null && this.devices.length > 0) {
          for (int i = 0; i < this.devices.length; i++) {
            com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.PartyOwnerData.DeviceInfo element = this.devices[i];
            if (element != null) {
              output.writeMessage(2, element);
            }
          }
        }
        if (this.party != null && this.party.length > 0) {
          for (int i = 0; i < this.party.length; i++) {
            com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.ActorDefinition element = this.party[i];
            if (element != null) {
              output.writeMessage(3, element);
            }
          }
        }
        if (this.npcs != null && this.npcs.length > 0) {
          for (int i = 0; i < this.npcs.length; i++) {
            com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.ActorDefinition element = this.npcs[i];
            if (element != null) {
              output.writeMessage(4, element);
            }
          }
        }
        if (!this.imageFile.equals("")) {
          output.writeString(5, this.imageFile);
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
        if (this.devices != null && this.devices.length > 0) {
          for (int i = 0; i < this.devices.length; i++) {
            com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.PartyOwnerData.DeviceInfo element = this.devices[i];
            if (element != null) {
              size += com.google.protobuf.nano.CodedOutputByteBufferNano
                .computeMessageSize(2, element);
            }
          }
        }
        if (this.party != null && this.party.length > 0) {
          for (int i = 0; i < this.party.length; i++) {
            com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.ActorDefinition element = this.party[i];
            if (element != null) {
              size += com.google.protobuf.nano.CodedOutputByteBufferNano
                .computeMessageSize(3, element);
            }
          }
        }
        if (this.npcs != null && this.npcs.length > 0) {
          for (int i = 0; i < this.npcs.length; i++) {
            com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.ActorDefinition element = this.npcs[i];
            if (element != null) {
              size += com.google.protobuf.nano.CodedOutputByteBufferNano
                .computeMessageSize(4, element);
            }
          }
        }
        if (!this.imageFile.equals("")) {
          size += com.google.protobuf.nano.CodedOutputByteBufferNano
              .computeStringSize(5, this.imageFile);
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
              int arrayLength = com.google.protobuf.nano.WireFormatNano
                  .getRepeatedFieldArrayLength(input, 18);
              int i = this.devices == null ? 0 : this.devices.length;
              com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.PartyOwnerData.DeviceInfo[] newArray =
                  new com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.PartyOwnerData.DeviceInfo[i + arrayLength];
              if (i != 0) {
                java.lang.System.arraycopy(this.devices, 0, newArray, 0, i);
              }
              for (; i < newArray.length - 1; i++) {
                newArray[i] = new com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.PartyOwnerData.DeviceInfo();
                input.readMessage(newArray[i]);
                input.readTag();
              }
              // Last one without readTag.
              newArray[i] = new com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.PartyOwnerData.DeviceInfo();
              input.readMessage(newArray[i]);
              this.devices = newArray;
              break;
            }
            case 26: {
              int arrayLength = com.google.protobuf.nano.WireFormatNano
                  .getRepeatedFieldArrayLength(input, 26);
              int i = this.party == null ? 0 : this.party.length;
              com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.ActorDefinition[] newArray =
                  new com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.ActorDefinition[i + arrayLength];
              if (i != 0) {
                java.lang.System.arraycopy(this.party, 0, newArray, 0, i);
              }
              for (; i < newArray.length - 1; i++) {
                newArray[i] = new com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.ActorDefinition();
                input.readMessage(newArray[i]);
                input.readTag();
              }
              // Last one without readTag.
              newArray[i] = new com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.ActorDefinition();
              input.readMessage(newArray[i]);
              this.party = newArray;
              break;
            }
            case 34: {
              int arrayLength = com.google.protobuf.nano.WireFormatNano
                  .getRepeatedFieldArrayLength(input, 34);
              int i = this.npcs == null ? 0 : this.npcs.length;
              com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.ActorDefinition[] newArray =
                  new com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.ActorDefinition[i + arrayLength];
              if (i != 0) {
                java.lang.System.arraycopy(this.npcs, 0, newArray, 0, i);
              }
              for (; i < newArray.length - 1; i++) {
                newArray[i] = new com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.ActorDefinition();
                input.readMessage(newArray[i]);
                input.readTag();
              }
              // Last one without readTag.
              newArray[i] = new com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.ActorDefinition();
              input.readMessage(newArray[i]);
              this.npcs = newArray;
              break;
            }
            case 42: {
              this.imageFile = input.readString();
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

    // repeated .PartyOwnerData.Group everything = 2;
    public com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.PartyOwnerData.Group[] everything;

    public PartyOwnerData() {
      clear();
    }

    public PartyOwnerData clear() {
      version = 0;
      everything = com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.PartyOwnerData.Group.emptyArray();
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
          com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.PartyOwnerData.Group element = this.everything[i];
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
          com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.PartyOwnerData.Group element = this.everything[i];
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
            com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.PartyOwnerData.Group[] newArray =
                new com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.PartyOwnerData.Group[i + arrayLength];
            if (i != 0) {
              java.lang.System.arraycopy(this.everything, 0, newArray, 0, i);
            }
            for (; i < newArray.length - 1; i++) {
              newArray[i] = new com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.PartyOwnerData.Group();
              input.readMessage(newArray[i]);
              input.readTag();
            }
            // Last one without readTag.
            newArray[i] = new com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.PartyOwnerData.Group();
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

  public static final class ActorDefinition extends
      com.google.protobuf.nano.MessageNano {

    private static volatile ActorDefinition[] _emptyArray;
    public static ActorDefinition[] emptyArray() {
      // Lazily initializes the empty array
      if (_emptyArray == null) {
        synchronized (
            com.google.protobuf.nano.InternalNano.LAZY_INIT_LOCK) {
          if (_emptyArray == null) {
            _emptyArray = new ActorDefinition[0];
          }
        }
      }
      return _emptyArray;
    }

    // optional string name = 1;
    public java.lang.String name;

    // optional uint32 level = 2;
    public int level;

    // optional uint32 experience = 3;
    public int experience;

    // optional string avatarFile = 4;
    public java.lang.String avatarFile;

    // repeated .ActorStatistics stats = 5;
    public com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.ActorStatistics[] stats;

    public ActorDefinition() {
      clear();
    }

    public ActorDefinition clear() {
      name = "";
      level = 0;
      experience = 0;
      avatarFile = "";
      stats = com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.ActorStatistics.emptyArray();
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
      if (this.experience != 0) {
        output.writeUInt32(3, this.experience);
      }
      if (!this.avatarFile.equals("")) {
        output.writeString(4, this.avatarFile);
      }
      if (this.stats != null && this.stats.length > 0) {
        for (int i = 0; i < this.stats.length; i++) {
          com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.ActorStatistics element = this.stats[i];
          if (element != null) {
            output.writeMessage(5, element);
          }
        }
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
      if (this.experience != 0) {
        size += com.google.protobuf.nano.CodedOutputByteBufferNano
            .computeUInt32Size(3, this.experience);
      }
      if (!this.avatarFile.equals("")) {
        size += com.google.protobuf.nano.CodedOutputByteBufferNano
            .computeStringSize(4, this.avatarFile);
      }
      if (this.stats != null && this.stats.length > 0) {
        for (int i = 0; i < this.stats.length; i++) {
          com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.ActorStatistics element = this.stats[i];
          if (element != null) {
            size += com.google.protobuf.nano.CodedOutputByteBufferNano
              .computeMessageSize(5, element);
          }
        }
      }
      return size;
    }

    @Override
    public ActorDefinition mergeFrom(
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
          case 24: {
            this.experience = input.readUInt32();
            break;
          }
          case 34: {
            this.avatarFile = input.readString();
            break;
          }
          case 42: {
            int arrayLength = com.google.protobuf.nano.WireFormatNano
                .getRepeatedFieldArrayLength(input, 42);
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
        }
      }
    }

    public static ActorDefinition parseFrom(byte[] data)
        throws com.google.protobuf.nano.InvalidProtocolBufferNanoException {
      return com.google.protobuf.nano.MessageNano.mergeFrom(new ActorDefinition(), data);
    }

    public static ActorDefinition parseFrom(
            com.google.protobuf.nano.CodedInputByteBufferNano input)
        throws java.io.IOException {
      return new ActorDefinition().mergeFrom(input);
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

    // optional uint32 initBonus = 1;
    public int initBonus;

    // optional int32 healthPoints = 2;
    public int healthPoints;

    public ActorStatistics() {
      clear();
    }

    public ActorStatistics clear() {
      initBonus = 0;
      healthPoints = 0;
      cachedSize = -1;
      return this;
    }

    @Override
    public void writeTo(com.google.protobuf.nano.CodedOutputByteBufferNano output)
        throws java.io.IOException {
      if (this.initBonus != 0) {
        output.writeUInt32(1, this.initBonus);
      }
      if (this.healthPoints != 0) {
        output.writeInt32(2, this.healthPoints);
      }
      super.writeTo(output);
    }

    @Override
    protected int computeSerializedSize() {
      int size = super.computeSerializedSize();
      if (this.initBonus != 0) {
        size += com.google.protobuf.nano.CodedOutputByteBufferNano
            .computeUInt32Size(1, this.initBonus);
      }
      if (this.healthPoints != 0) {
        size += com.google.protobuf.nano.CodedOutputByteBufferNano
            .computeInt32Size(2, this.healthPoints);
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
            this.initBonus = input.readUInt32();
            break;
          }
          case 16: {
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

  public static final class PartyClientData extends
      com.google.protobuf.nano.MessageNano {

    public static final class Group extends
        com.google.protobuf.nano.MessageNano {

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

      // optional bytes key = 2;
      public byte[] key;

      public Group() {
        clear();
      }

      public Group clear() {
        name = "";
        key = com.google.protobuf.nano.WireFormatNano.EMPTY_BYTES;
        cachedSize = -1;
        return this;
      }

      @Override
      public void writeTo(com.google.protobuf.nano.CodedOutputByteBufferNano output)
          throws java.io.IOException {
        if (!this.name.equals("")) {
          output.writeString(1, this.name);
        }
        if (!java.util.Arrays.equals(this.key, com.google.protobuf.nano.WireFormatNano.EMPTY_BYTES)) {
          output.writeBytes(2, this.key);
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
        if (!java.util.Arrays.equals(this.key, com.google.protobuf.nano.WireFormatNano.EMPTY_BYTES)) {
          size += com.google.protobuf.nano.CodedOutputByteBufferNano
              .computeBytesSize(2, this.key);
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
              this.key = input.readBytes();
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

    private static volatile PartyClientData[] _emptyArray;
    public static PartyClientData[] emptyArray() {
      // Lazily initializes the empty array
      if (_emptyArray == null) {
        synchronized (
            com.google.protobuf.nano.InternalNano.LAZY_INIT_LOCK) {
          if (_emptyArray == null) {
            _emptyArray = new PartyClientData[0];
          }
        }
      }
      return _emptyArray;
    }

    // optional uint32 version = 1;
    public int version;

    // repeated .PartyClientData.Group everything = 2;
    public com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.PartyClientData.Group[] everything;

    public PartyClientData() {
      clear();
    }

    public PartyClientData clear() {
      version = 0;
      everything = com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.PartyClientData.Group.emptyArray();
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
          com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.PartyClientData.Group element = this.everything[i];
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
          com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.PartyClientData.Group element = this.everything[i];
          if (element != null) {
            size += com.google.protobuf.nano.CodedOutputByteBufferNano
              .computeMessageSize(2, element);
          }
        }
      }
      return size;
    }

    @Override
    public PartyClientData mergeFrom(
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
            com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.PartyClientData.Group[] newArray =
                new com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.PartyClientData.Group[i + arrayLength];
            if (i != 0) {
              java.lang.System.arraycopy(this.everything, 0, newArray, 0, i);
            }
            for (; i < newArray.length - 1; i++) {
              newArray[i] = new com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.PartyClientData.Group();
              input.readMessage(newArray[i]);
              input.readTag();
            }
            // Last one without readTag.
            newArray[i] = new com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage.PartyClientData.Group();
            input.readMessage(newArray[i]);
            this.everything = newArray;
            break;
          }
        }
      }
    }

    public static PartyClientData parseFrom(byte[] data)
        throws com.google.protobuf.nano.InvalidProtocolBufferNanoException {
      return com.google.protobuf.nano.MessageNano.mergeFrom(new PartyClientData(), data);
    }

    public static PartyClientData parseFrom(
            com.google.protobuf.nano.CodedInputByteBufferNano input)
        throws java.io.IOException {
      return new PartyClientData().mergeFrom(input);
    }
  }
}
