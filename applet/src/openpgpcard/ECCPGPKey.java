package openpgpcard;

import com.nxp.id.jcopx.SignatureX;

import javacard.framework.ISOException;
import javacard.framework.Util;
import javacard.security.ECPrivateKey;
import javacard.security.ECPublicKey;
import javacard.security.KeyAgreement;
import javacard.security.KeyPair;
import javacard.security.Signature;

public class ECCPGPKey extends PGPKey {
	private final KeyPair key;
	private final byte[] oid;
	private final byte type;
	private static Signature signature = null;
	private static KeyAgreement agreement = null;
	
	public static final byte CURVE_P256R1 = 1;
		
	public ECCPGPKey(byte type) {
		this(type, CURVE_P256R1);
	}
	
	public ECCPGPKey(byte type, byte curve) {
		if(curve == CURVE_P256R1) {
			key = SecP256r1.newKeyPair();
			oid = SecP256r1.oid;
		} else {
			key = null;
			oid = null;
		}
		this.type = type;
		
		if(type == ALGO_ECDSA && signature == null) {
			signature = SignatureX.getInstance(SignatureX.ALG_ECDSA_PLAIN, false);
		} else if(type == ALGO_ECDH && agreement == null) {
			agreement = KeyAgreement.getInstance(KeyAgreement.ALG_EC_SVDP_DH, false);
		}
	}

	public void genKeyPair() {
		key.genKeyPair();
	}

	public short getAttributes(byte[] data, short offset) {
		data[offset++] = (byte) (oid.length + 1);
		data[offset++] = type;
		return Util.arrayCopy(oid, (short) 0, data, offset, (short) oid.length);
	}

	public boolean isInitialized() {
		return getPrivate().isInitialized();
	}
	
	public short getPublicKey(byte[] data, short offset) {
		data[offset++] = 0x06;
		data[offset++] = (byte) oid.length;
		Util.arrayCopy(oid, (short)0, data, offset, (short) oid.length);
		offset += oid.length;
		data[offset++] = (byte) 0x86;
		ECPublicKey pubKey = getPublic();
		short publen = pubKey.getW(data, (short) (offset + 1));
		data[offset++] = (byte) publen;
		offset += publen;
		return offset;
	}
	
	private ECPublicKey getPublic() {
		return (ECPublicKey) key.getPublic();
	}
	
	private ECPrivateKey getPrivate() {
		return (ECPrivateKey) key.getPrivate();
	}

	public void setPrivateKey(byte[] data, short offset) {
		// Skip empty length of CRT
		offset++;

		// Check for tag 7F48
		if (data[offset++] != 0x7F || data[offset++] != 0x48)
			ISOException.throwIt(SW_DATA_INVALID);
		short len_template = OpenPGPUtil.getLength(data, offset);
		offset += OpenPGPUtil.getLengthBytes(len_template);

		short offset_data = (short) (offset + len_template);

		/* apparently tag 0x91 is used for ecc private key */
		if (data[offset++] != (byte) 0x91)
			ISOException.throwIt(SW_DATA_INVALID);
		short len_e = OpenPGPUtil.getLength(data, offset);
		offset += OpenPGPUtil.getLengthBytes(len_e);
		
		if (data[offset_data++] != 0x5F || data[offset_data++] != 0x48)
			ISOException.throwIt(SW_DATA_INVALID);
		offset_data += OpenPGPUtil.getLengthBytes(OpenPGPUtil.getLength(data, offset_data));
		
		getPrivate().setS(data, offset_data, len_e);
		offset_data += len_e;
	}

	
	public short decrypt(byte[] inData, short inOffs, short inLen,
			byte[] outData, short outOffs) {
		agreement.init(getPrivate());
		return agreement.generateSecret(inData, inOffs, inLen, outData, outOffs);
	}

	
	public short sign(byte[] inData, short inOffs, short inLen, byte[] outData,
			short outOffs) {
		signature.init(getPrivate(), Signature.MODE_SIGN);
		return signature.sign(inData, inOffs, inLen, outData, outOffs);
	}
}
