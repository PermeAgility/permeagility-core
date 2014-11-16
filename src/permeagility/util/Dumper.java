package permeagility.util;

public class Dumper {

	public static String hexDump(byte[] data, int position, int length){
		int lineCounter = 0;
		String lineCounterS = Integer.toHexString(lineCounter).toUpperCase();
		StringBuffer result = new StringBuffer();
		result.append("Length: ");
		result.append(length);
		result.append("\n0x00");
		result.append(lineCounterS);
		result.append(")");
		StringBuffer printable = new StringBuffer();
		for(int i = 0; i < length; i++){
			if( i%16 == 0 && i!=0 ){
				printable = new StringBuffer();
				result.append("\n");
				lineCounter+= 16;
				lineCounterS = Integer.toHexString(lineCounter).toUpperCase(); 
				result.append("0x");
				if(lineCounterS.length() < 2)
					result.append("0");
				if(lineCounterS.length() < 3)
					result.append("0");
				result.append(lineCounterS);
				result.append(")");
			}
			result.append(" ");			
			if((data[position +i ] & 0xf0)  == 0)
				result.append("0");
			result.append(Integer.toHexString(data[position + i] & 0xff).toUpperCase());
			if (Character.isLetterOrDigit((char)data[position+i]) ) {
				printable.append((char)data[position+i]);
			} else {
				printable.append(" ");
			}
			if( (i+1)%16 == 0){
				result.append(" ");
				result.append(printable.toString());
			}
		}
		return result.toString();
	}

	public static String hexDump(byte[] data){
		return hexDump(data, 0, data.length);
	}

}
