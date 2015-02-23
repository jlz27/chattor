package protocol;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class Message implements Serializable {
	private static final long serialVersionUID = 1L;
	
	private MessageType type;
	private Map<DataType, Serializable> objectMap;
	
	public Message(MessageType type) {
		this.type = type;
		this.objectMap = new HashMap<DataType, Serializable>();
	}
	
	public void addData(DataType dataType, Serializable obj) {
		this.objectMap.put(dataType, obj);
	}
	
	public Map<DataType, Serializable> getData(){
		return this.objectMap;
	}
	
	public MessageType getType() {
		return this.type;
	}
}
