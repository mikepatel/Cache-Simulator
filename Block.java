/* Michael Patel
 * ECE 521
 * Project 1
 * Spring 2017
 */

public class Block {
	
	String L1_tag;
	int L1_index;
	int L1_LRUcount;
	int L1_FIFOcount;
	boolean L1_validFlag;
	boolean L1_dirtyFlag;
	String L1_address;
	
	String L2_tag;
	int L2_index;
	int L2_LRUcount;
	int L2_FIFOcount;
	boolean L2_validFlag;
	boolean L2_dirtyFlag;
	String L2_address;
	
	// Constructor
	public Block(){
		L1_tag = "";
		L1_index = 0;
		L1_LRUcount = 0;
		L1_FIFOcount = 0;
		L1_validFlag = false;
		L1_dirtyFlag = false;
		L1_address = "";
		
		L2_tag = "";
		L2_index = 0;
		L2_LRUcount = 0;
		L2_FIFOcount = 0;
		L2_validFlag = false;
		L2_dirtyFlag = false;
		L2_address = "";
	}

}
