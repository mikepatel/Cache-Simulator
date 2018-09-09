import java.io.File;
import java.io.FileNotFoundException;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.util.Scanner;

/* Michael Patel
 * ECE 521
 * Spring 2017
 * Project 1
 */

/* WB-WA policy
 *  
 */


public class sim_cache { // using lowercase for class name to match "sim_cache.java" by spec

	// GLOBALS, instance variables
	static int blockSize;
	static int L1_size;
	static int L1_associativity;
	static int L2_size; // 0 -> no L2 cache
	static int L2_associativity;
	static int replacementPolicy_int; // 0->LRU, 1->FIFO
	static int inclusion_int; // 0->NI, 1->I, 2->E
	static String traceFile;

	static Block[][] L1_cache;
	static Block[][] L2_cache;

	static String replacementPolicy = "";
	static String inclusionPolicy = "";

	static int num_L1_reads;
	static int num_L1_read_misses;
	static int num_L1_writes;
	static int num_L1_write_misses;
	static double L1_miss_rate;
	static int num_L1_writebacks;
	static int num_L2_reads;
	static int num_L2_read_misses;
	static int num_L2_writes;
	static int num_L2_write_misses;
	static double L2_miss_rate;
	static int num_L2_writebacks;
	static int totalMemoryTraffic;

	static int L1_BOwidth=0;
	static int L1_indexWidth=0;
	static int L1_tagWidth=0;
	static int L2_BOwidth=0;
	static int L2_indexWidth=0;
	static int L2_tagWidth=0;

	static int L1_numIndices=0;
	static int L1_numTags=0;
	static int L2_numIndices=0;
	static int L2_numTags=0;

	static boolean L2exists = false;

	static boolean L1_read_isHit = false;
	static boolean L2_read_isHit = false;
	static boolean L1_write_isHit = false;
	static boolean L2_write_isHit = false;

	static boolean L1_read_allUsed = false;
	static boolean L2_read_allUsed = false;
	static boolean L1_write_allUsed = false;
	static boolean L2_write_allUsed = false;

	static DecimalFormat df = new DecimalFormat("#0.000000"); // format to 6 decimal places

	static String L1_inTag = "";
	static String L1_inIndex = "";
	static String L2_inTag = "";
	static String L2_inIndex = "";
	static int L1_indexInt = 0;
	static int L2_indexInt = 0;

	static String requestType = "";
	static String hexAddress = "";
	static String binaryAddress = "";

	static String L1_evictAddress = "";
	static String L1_evictTag = "";
	static String L1_evictIndexString = "";
	static int L1_evictIndex = 0;

	static String L2_evictAddress = "";
	static String L2_evictTag = "";
	static String L2_evictIndexString = "";
	static int L2_evictIndex = 0;
	
	static boolean L1_excl_evict_dirty=false;
	static boolean L2_excl_dirty=false;

	/*********************************************************************************************/

	// Constructor
	public sim_cache(String[] args) {
		// specific input argument order provided by spec
		blockSize = Integer.parseInt(args[0]);
		L1_size = Integer.parseInt(args[1]);
		L1_associativity = Integer.parseInt(args[2]);
		L2_size = Integer.parseInt(args[3]);
		L2_associativity = Integer.parseInt(args[4]);
		replacementPolicy_int = Integer.parseInt(args[5]);
		inclusion_int = Integer.parseInt(args[6]);
		traceFile = args[7];
		
		// reset counters displayed to console output
		setResultCountersToZero();

		// calculations to determine geometry of L1 cache, index and BO width
		if(L1_size > 0){
			build_L1_cache();		
			calculate_L1_IndexAndBOwidth();
		}

		// calculations to determine geometry of L2 cache, index and BO width
		if(L2_size > 0){ 
			L2exists = true;
			build_L2_cache();
			calculate_L2_IndexAndBOwidth();
		}
		else{
			L2exists = false;
		}

		// configure cache properties (Replacement and Inclusion)
		configureReplacementPolicy(); // 0->LRU, 1->FIFO
		configureInclusionPolicy(); // 0->NI, 1->I, 2->E

	} // end of constructor


	/*********************************************************************************************/


	// Main
	public static void main(String[] args){
		sim_cache sc = new sim_cache(args); // create empty cache
		
		if(inclusionPolicy.equals("exclusive")){
			executeOrder66();
			System.exit(0);
		}

		// Inclusive, NINE Policy
		try {
			// read operations and addresses in from trace file
			Scanner scanner = new Scanner(new File(traceFile));

			while(scanner.hasNext()){ // tokens still available to read, not end of file					
				// read in 'r' or 'w'
				requestType = scanner.next().trim();

				// read in address (as hex)
				hexAddress = scanner.next().trim();

				// convert address: Hex to Binary String
				binaryAddress = hexToBinaryString(hexAddress);

				prepForRW_L1(); // L1 - calculate tag width, tag String, index String and index int
				if(L2exists){
					prepForRW_L2(); // L2 - calculate tag width, tag String, index String and index int
				}					

				// HANDLE READS
				if(requestType.equals("r")){
					run_L1_NI_Read_engine(); // L1 -> L2
				}

				// HANDLE WRITES
				if(requestType.equals("w")){ // L1 -> L2
					run_L1_NI_Write_engine();
				}

			} // end of scanner-while

			scanner.close(); // end of file

		} catch (FileNotFoundException e) {
			System.out.println(e.toString());
			e.printStackTrace();
		}
		//break;

		/***************************/
		// INCLUSIVE
		//case "inclusive":
		//	break;

		/***************************/
		// EXCLUSIVE
		//case "exclusive":
		//	break;

		/***************************/
		//default: break;
		//}

		/***************************/
		// statistical calculations for console output
		get_L1_missRate();
		if(L2exists){
			get_L2_missRate();
		}		

		// calculations to find total memory traffic
		if(L2exists){
			totalMemoryTraffic = num_L2_writebacks + num_L2_read_misses + num_L2_write_misses; // WB -> To Memory, RM+WM -> From Memory
		}
		else{
			totalMemoryTraffic = num_L1_writebacks + num_L1_read_misses + num_L1_write_misses; // WB -> To Memory, RM+WM -> From Memory
		}

		generateOutput(); // generate output to console

	} // end of main


	/**********************************************************************************************/
	// L1 READ engine
	private static void run_L1_NI_Read_engine(){
		num_L1_reads++; // increment L1 reads count
		L1_read_isHit = false; // assume hit not found in L1
		L1_read_allUsed = true; // assume fully valid L1 cache

		run_L1_NI_Read_Hits_engine(); // L1 - READ - HITS

		if(L1_read_isHit){
			return; // found hit, just continue
		}
		else{ // L1 - READ - MISSES
			run_L1_NI_Read_Misses_engine();			
		}
	}

	/**********************************************************************************************/
	// L1 READ HITS engine
	private static void run_L1_NI_Read_Hits_engine(){
		// L1 - READ - HITS	
		for(int i=0; i<L1_numTags; i++){ // look for tag matching
			if(L1_cache[L1_indexInt][i].L1_validFlag){ // check the used blocks first
				if(L1_cache[L1_indexInt][i].L1_tag.equals(L1_inTag)){
					L1_read_isHit = true; // found a matching tag

					if(replacementPolicy.equals("FIFO")){
						// FIFO, don't need to worry about FIFO count for read-hits
					}

					else{
						// LRU
						// increment LRU counters for others
						for(int j=0; j<L1_numTags; j++){
							if(L1_cache[L1_indexInt][j].L1_LRUcount < L1_cache[L1_indexInt][i].L1_LRUcount){
								L1_cache[L1_indexInt][j].L1_LRUcount++;
							}											
						}

						L1_cache[L1_indexInt][i].L1_LRUcount = 0; // MRU
					}					

					break; // found hit, break from tag-matching-loop
				}
				else{
					L1_read_isHit = false;
				}
			} // end if-valid
		} // end for-loop for L1 tag matching
	}

	/**********************************************************************************************/
	// L1 READ MISSES engine
	private static void run_L1_NI_Read_Misses_engine(){
		num_L1_read_misses++; // increment L1 misses count		

		// select candidate for eviction
		// Some Unused
		for(int i=0; i<L1_numTags; i++){
			// in Set, are there any unused blocks? (valid bits)
			if(!L1_cache[L1_indexInt][i].L1_validFlag){
				// located an empty block

				// LRU
				if(replacementPolicy.equals("LRU")){
					// increment LRU counters for others
					for(int j=0; j<L1_numTags; j++){
						if(L1_cache[L1_indexInt][j].L1_validFlag){
							L1_cache[L1_indexInt][j].L1_LRUcount++;
						}											
					}
				}									

				// install block into L1 for "RM - some unused" - install current block into L1 (update block properties)
				L1_cache[L1_indexInt][i].L1_tag = L1_inTag;
				L1_cache[L1_indexInt][i].L1_validFlag = true;
				L1_cache[L1_indexInt][i].L1_dirtyFlag = false; // read operation, assume fresh install from Main Memory, let L2 code handle if Exclusive+L2RH
				L1_cache[L1_indexInt][i].L1_FIFOcount = i;
				L1_cache[L1_indexInt][i].L1_LRUcount = 0; // MRU	
				L1_cache[L1_indexInt][i].L1_address = binaryAddress; // save current address

				L1_read_allUsed = false; // found an empty block in Set, no need to evict

				break; // replaced block, so break
			}
		} // end of L2 - Read - Misses - some unused - for-loop

		// All blocks are used -> Eviction
		if(L1_read_allUsed){ // all blocks are used (all valid bits TRUE), need to evict+replace, implement replacement policy

			// FIFO
			if(replacementPolicy.equals("FIFO")){
				// FIFO
				for(int i=0; i<L1_numTags; i++){
					if(L1_cache[L1_indexInt][i].L1_FIFOcount == 0){
						// located a block to evict

						// decrement FIFO count for all others
						for(int j=0; j<L1_numTags; j++){
							if(L1_cache[L1_indexInt][j].L1_validFlag){
								L1_cache[L1_indexInt][j].L1_FIFOcount--;
							}
						}

						// evicted L1 block moved to L2
						if(inclusionPolicy.equals("exclusive")){ // Exclusive Policy
							if(L1_cache[L1_indexInt][i].L1_dirtyFlag){
								num_L1_writebacks++;
								L1_excl_evict_dirty = true; // soon-to-be evicted L1 block is dirty -> moved to L2
							}
							else{
								L1_excl_evict_dirty = false; // soon-to-be evicted L1 block is clean -> moved to L2
							}
							
							// Write evicted L1 block back to L2, regardless of whether is dirty or clean
							L1_evictAddress = L1_cache[L1_indexInt][i].L1_address; // put L1 address into L1 evict address before overwriting
							run_L2_NI_Write_engine(); // L2 Write		
						}
						
						else{ // Inclusive, NINE Policy
							// check whether need to write evicted L1 block back to L2
							if(L1_cache[L1_indexInt][i].L1_dirtyFlag){							
								L1_evictAddress = L1_cache[L1_indexInt][i].L1_address; // put L1 address into L1 evict address before overwriting
								run_L2_NI_Write_engine(); // L2 Write
								num_L1_writebacks++;
							}
						}						

						// "RM - All Used - FIFO" - install current block into L1
						L1_cache[L1_indexInt][i].L1_tag = L1_inTag;
						L1_cache[L1_indexInt][i].L1_validFlag = true;
						L1_cache[L1_indexInt][i].L1_dirtyFlag = false; // read operation, assume fresh install from Main Memory, let L2 code handle if Exclusive+L2RH
						L1_cache[L1_indexInt][i].L1_FIFOcount = L1_numTags - 1; // Last one in
						L1_cache[L1_indexInt][i].L1_address = binaryAddress; // save current address
						// don't mess with LRU count

						break; // replaced block, so break

					} // end if-L1-FIFOcount
				} // end looking for-loop eviction
			} // end of FIFO

			// LRU
			else{
				// LRU
				for(int i=0; i<L1_numTags; i++){
					if(L1_cache[L1_indexInt][i].L1_LRUcount == (L1_numTags - 1)){
						// located a block to evict

						// increment LRU counters for others
						for(int j=0; j<L1_numTags; j++){
							if(L1_cache[L1_indexInt][j].L1_validFlag){
								L1_cache[L1_indexInt][j].L1_LRUcount++;
							}
						}

						// swap evicted L1 block to L2
						if(inclusionPolicy.equals("exclusive")){ // Exclusive Policy
							if(L1_cache[L1_indexInt][i].L1_dirtyFlag){
								num_L1_writebacks++;
								L1_excl_evict_dirty = true; // soon-to-be evicted L1 block is dirty -> moved to L2
							}
							else{
								L1_excl_evict_dirty = false; // soon-to-be evicted L1 block is clean -> moved to L2
							}
							
							// Write evicted L1 block back to L2, regardless of whether is dirty or clean
							L1_evictAddress = L1_cache[L1_indexInt][i].L1_address; // put L1 address into L1 evict address before overwriting
							run_L2_NI_Write_engine(); // L2 Write
						} // end Exclusive
						
						else{ // Inclusive, NINE Policy
							// check whether need to write evicted L1 block back to L2
							if(L1_cache[L1_indexInt][i].L1_dirtyFlag){
								L1_evictAddress = L1_cache[L1_indexInt][i].L1_address; // put L1 address into L1 evict address before overwriting
								run_L2_NI_Write_engine(); // L2 Write	
								num_L1_writebacks++;
							}
						}						

						// "RM - All Used - LRU" - install current block into L1
						L1_cache[L1_indexInt][i].L1_tag = L1_inTag;
						L1_cache[L1_indexInt][i].L1_validFlag = true;
						L1_cache[L1_indexInt][i].L1_dirtyFlag = false; // read operation, assume fresh install from Main Memory, let L2 code handle if Exclusive+L2RH
						L1_cache[L1_indexInt][i].L1_LRUcount = 0; // MRU
						L1_cache[L1_indexInt][i].L1_address = binaryAddress; // save current address

						break; // replaced block, so break
					} // end if-LRU == max LRU count
				} // end for-loop for tag matching
			} // end LRU
		} // end of if-all-blocks-used

		run_L2_NI_Read_engine(); // L2 Read
	}

	/**********************************************************************************************/
	// L1 WRITE engine
	private static void run_L1_NI_Write_engine(){
		num_L1_writes++; 
		L1_write_isHit = false; // assume hit not found in L1
		L1_write_allUsed = true; // assume fully valid L1 cache

		run_L1_NI_Write_Hits_engine(); // L1 - WRITE - HITS

		if(L1_write_isHit){
			return; // found hit, just continue
		}
		else{ // L1 - WRITE - MISSES
			run_L1_NI_Write_Misses_engine();
		}
	}

	/**********************************************************************************************/
	// L1 WRITE HITS engine
	private static void run_L1_NI_Write_Hits_engine(){
		// L1 - WRITE - HITS
		for(int i=0; i<L1_numTags; i++){
			if(L1_cache[L1_indexInt][i].L1_validFlag){ // check used (valid) blocks first
				if(L1_cache[L1_indexInt][i].L1_tag.equals(L1_inTag)){ // looking for tag matching
					L1_write_isHit = true; // found tag match
					L1_cache[L1_indexInt][i].L1_dirtyFlag = true; // write, modifying in L1 cache

					if(replacementPolicy.equals("FIFO")){
						// FIFO, do nothing with FIFO count
					}
					else{
						// LRU
						// increment LRU counters for others
						for(int j=0; j<L1_numTags; j++){
							if(L1_cache[L1_indexInt][j].L1_LRUcount < L1_cache[L1_indexInt][i].L1_LRUcount){
								L1_cache[L1_indexInt][j].L1_LRUcount++;
							}											
						}

						L1_cache[L1_indexInt][i].L1_LRUcount = 0; // MRU
					}

					break; // found hit, break from tag-matching-loop
				}
				else{
					L1_write_isHit = false;
				}

			} // end of if-valid
		} // end of for-loop for tag matching
	}

	/**********************************************************************************************/
	// L1 WRITE MISSES engine
	private static void run_L1_NI_Write_Misses_engine(){
		num_L1_write_misses++;


		// select L1 candidate for eviction
		// Some Used
		for(int i=0; i<L1_numTags; i++){
			// in Set, are there any unused blocks? (valid bits)
			if(!L1_cache[L1_indexInt][i].L1_validFlag){
				// located an empty block

				// LRU
				if(replacementPolicy.equals("LRU")){
					// increment LRU counters for others
					for(int j=0; j<L1_numTags; j++){
						if(L1_cache[L1_indexInt][j].L1_validFlag){
							L1_cache[L1_indexInt][j].L1_LRUcount++;
						}											
					}
				}

				// "WM - Some Unused" - install current block into L1
				L1_cache[L1_indexInt][i].L1_tag = L1_inTag;
				L1_cache[L1_indexInt][i].L1_validFlag = true;
				L1_cache[L1_indexInt][i].L1_dirtyFlag = true; // write operation, assume fresh install from Main Memory, let L2 code handle if Exclusive+L2RH
				L1_cache[L1_indexInt][i].L1_FIFOcount = i;
				L1_cache[L1_indexInt][i].L1_LRUcount = 0; // MRU
				L1_cache[L1_indexInt][i].L1_address = binaryAddress; // save current address

				L1_write_allUsed = false;

				break; // replaced block, so break
			} // 
		}

		// All blocks are used
		if(L1_write_allUsed){ // all blocks are used (all valid bits TRUE), need to replace

			// FIFO
			if(replacementPolicy.equals("FIFO")){
				// FIFO
				for(int i=0; i<L1_numTags; i++){
					if(L1_cache[L1_indexInt][i].L1_FIFOcount == 0){
						// located block to evict

						// decrement FIFO count for all others
						for(int j=0; j<L1_numTags; j++){
							if(L1_cache[L1_indexInt][j].L1_validFlag){
								L1_cache[L1_indexInt][j].L1_FIFOcount--;
							}
						}

						// swap evicted L1 block to L2
						if(inclusionPolicy.equals("exclusive")){ // Exclusive Policy
							if(L1_cache[L1_indexInt][i].L1_dirtyFlag){
								num_L1_writebacks++;
								L1_excl_evict_dirty = true; // soon-to-be evicted L1 block is dirty -> moved to L2
							}
							else{
								L1_excl_evict_dirty = false; // soon-to-be evicted L1 block is clean -> moved to L2
							}
							// Write evicted L1 block back to L2, regardless of whether is dirty or clean
							L1_evictAddress = L1_cache[L1_indexInt][i].L1_address; // put L1 address into L1 evict address before overwriting
							run_L2_NI_Write_engine(); // L2 Write
														
						}
						else{ // Inclusive, NINE Policy
							// check whether need to write evicted L1 block back to L2
							if(L1_cache[L1_indexInt][i].L1_dirtyFlag){							
								L1_evictAddress = L1_cache[L1_indexInt][i].L1_address; // put L1 address into L1 evict address before overwriting
								run_L2_NI_Write_engine(); // L2 Write
								num_L1_writebacks++;
							}
						}						

						// "WM - All Used - FIFO" - install current block into L1
						L1_cache[L1_indexInt][i].L1_tag = L1_inTag;
						L1_cache[L1_indexInt][i].L1_validFlag = true;
						L1_cache[L1_indexInt][i].L1_dirtyFlag = true; // write operation, assume fresh install from Main Memory, let L2 code handle if Exclusive+L2RH
						L1_cache[L1_indexInt][i].L1_FIFOcount = L1_numTags - 1;
						L1_cache[L1_indexInt][i].L1_address = binaryAddress; // save current address

						break; // replaced block, so break
					} // end of if-FIFOcount
				} // end for-loop for FIFO eviction
			} // end of FIFO

			// LRU
			else{
				// LRU
				for(int i=0; i<L1_numTags; i++){
					if(L1_cache[L1_indexInt][i].L1_LRUcount == (L1_numTags - 1)){
						// located a block to evict

						// increment LRU counters for others
						for(int j=0; j<L1_numTags; j++){
							if(L1_cache[L1_indexInt][j].L1_validFlag){
								L1_cache[L1_indexInt][j].L1_LRUcount++;
							}
						}

						// evicted L1 block moved to L2
						if(inclusionPolicy.equals("exclusive")){ // Exclusive Policy
							if(L1_cache[L1_indexInt][i].L1_dirtyFlag){
								num_L1_writebacks++;
								L1_excl_evict_dirty = true; // soon-to-be evicted L1 block is dirty -> moved to L2
							}
							else{
								L1_excl_evict_dirty = false; // soon-to-be evicted L1 block is clean -> moved to L2
							}
							// Write evicted L1 block back to L2, regardless of whether is dirty or clean
							L1_evictAddress = L1_cache[L1_indexInt][i].L1_address; // put L1 address into L1 evict address before overwriting
							run_L2_NI_Write_engine(); // L2 Write							
						}
						
						else{ // Inclusive, NINE Policy
							// check whether need to write evicted L1 block back to L2
							if(L1_cache[L1_indexInt][i].L1_dirtyFlag){							
								L1_evictAddress = L1_cache[L1_indexInt][i].L1_address; // put L1 address into L1 evict address before overwriting
								run_L2_NI_Write_engine(); // L2 Write		
								num_L1_writebacks++;
							}
						}						

						// "WM - All Used - LRU" install current block into L1
						L1_cache[L1_indexInt][i].L1_tag = L1_inTag;
						L1_cache[L1_indexInt][i].L1_validFlag = true;
						L1_cache[L1_indexInt][i].L1_dirtyFlag = true; // write operation, assume fresh install from Main Memory, let L2 code handle if Exclusive+L2RH
						L1_cache[L1_indexInt][i].L1_LRUcount = 0;
						L1_cache[L1_indexInt][i].L1_address = binaryAddress; // save current address

						break; // replaced block, so break
					} // end if-LRU == max LRU count
				} // end for-loop for tag matching
			} // end of LRU
		} // end of L1 if-all blocks used

		run_L2_NI_Read_engine(); // L2 Read
	}

	/**********************************************************************************************/
	// L2 READ engine
	private static void run_L2_NI_Read_engine(){
		// NON-INCLUSIVE
		// L2 - HANDLE READS
		if(L2exists){ // begin L2 - Reads (from L1 Read Misses)
			num_L2_reads++;
			L2_read_isHit = false; // assume hit not found in L2
			L2_read_allUsed = true; // assume fully valid L2 cache

			run_L2_NI_Read_Hits_engine(); // L2 - READ - HITS

			if(L2_read_isHit){
				return; // hit found, just continue
			}
			else{ // L2 - READ - MISSES
				run_L2_NI_Read_Misses_engine();
			}
		}
	}

	/**********************************************************************************************/
	// L2 READ HITS engine
	private static void run_L2_NI_Read_Hits_engine(){
		// L2 - READ - HITS
		for(int i=0; i<L2_numTags; i++){ // look for L2 tag matching
			if(L2_cache[L2_indexInt][i].L2_validFlag){ // check the used blocks first
				if(L2_cache[L2_indexInt][i].L2_tag.equals(L2_inTag)){ // look for tag matching
					L2_read_isHit = true; // found tag match, current block in L2

					if(replacementPolicy.equals("FIFO")){
						// FIFO, don't need to worry about FIFO count for read-hits
					} // end of FIFO
					else{
						// LRU
						// increment LRU counters for others
						for(int j=0; j<L2_numTags; j++){
							if(L2_cache[L2_indexInt][j].L2_LRUcount < L2_cache[L2_indexInt][i].L2_LRUcount){
								L2_cache[L2_indexInt][j].L2_LRUcount++;
							}
						}

						L2_cache[L2_indexInt][i].L2_LRUcount = 0; // MRU
					} // end of LRU

					// L2 - Exclusion Policy
					if(inclusionPolicy.equals("exclusive")){
						// L2 RH - swap to L1, so can no longer exist in L2
						// assume block is being installed into L1
						L2_cache[L2_indexInt][i].L2_validFlag = false; // invalidate block in L2, since it exists in L1
						
						if(L2_cache[L2_indexInt][i].L2_dirtyFlag){ // essentially evicting block from L2, so check if need to writeback
							num_L2_writebacks++;
						}
						
						L2_evictAddress = L2_cache[L2_indexInt][i].L2_address; // save address of evicted L2 block b/c will move it to L1
						get_L2_EvictParam(); // use L2 evictAddress to get L2 evictTag and L2 evictIndex, which have L1 geometries
						
						// in L1
						for(int j=0; j<L1_numTags; j++){ // look for tag matching in L1
							if(L1_cache[L2_evictIndex][j].L1_validFlag){ // if block exists (assume it should b/c want to overwrite L1 fresh install if L2RH)
								if(L1_cache[L2_evictIndex][j].L1_tag.equals(L2_evictTag)){ 
									// found the copy of evicted L2 block in L1
									L1_cache[L2_evictIndex][j].L1_tag = L2_evictTag;
									L1_cache[L2_evictIndex][j].L1_validFlag = true;
									L1_cache[L2_evictIndex][j].L1_dirtyFlag = L2_cache[L2_indexInt][i].L2_dirtyFlag; // copy L2 dirty to L1
									L1_cache[L2_evictIndex][j].L1_FIFOcount = L1_numTags - 1;
									L1_cache[L2_evictIndex][j].L1_LRUcount = 0; // MRU
									L1_cache[L2_evictIndex][j].L1_address = L2_evictAddress;
									
									break; // completed L2 -> L1 swap
								}
							}
						}
						
					} // end Exclusion

					break; // found hit, break from tag-matching loop
				} // end of if-tag match
				else{
					L2_read_isHit = false;
				}
			} // end if-valid
		} // end for-loop for L2 tag matching
	}

	/**********************************************************************************************/
	// L2 READ MISSES engine
	private static void run_L2_NI_Read_Misses_engine(){
		// L2 - READ - MISSES
		num_L2_read_misses++; // increment L2 misses count

		if(!inclusionPolicy.equals("exclusive")){ // Inclusive, NINE Policy

			// select candidate for eviction
			// Some Unused
			for(int i=0; i<L2_numTags; i++){
				// in Set, are there any unused blocks? (valid bits)
				if(!L2_cache[L2_indexInt][i].L2_validFlag){
					// located empty block

					// LRU
					if(replacementPolicy.equals("LRU")){
						// increment LRU counters for others
						for(int j=0; j<L2_numTags; j++){
							if(L2_cache[L2_indexInt][j].L2_validFlag){
								L2_cache[L2_indexInt][j].L2_LRUcount++;
							}
						}
					}

					// "RM - Some Unused" - install current block into L2 (update block properties)
					L2_cache[L2_indexInt][i].L2_tag = L2_inTag;
					L2_cache[L2_indexInt][i].L2_validFlag = true;
					L2_cache[L2_indexInt][i].L2_dirtyFlag = false; // read
					L2_cache[L2_indexInt][i].L2_FIFOcount = i;
					L2_cache[L2_indexInt][i].L2_LRUcount = 0; // MRU
					L2_cache[L2_indexInt][i].L2_address = binaryAddress; // save current address


					L2_read_allUsed = false;

					break; // replaced block, so break
				}
			}

			// All blocks are used -> Eviction
			if(L2_read_allUsed){

				// FIFO
				if(replacementPolicy.equals("FIFO")){
					// FIFO
					for(int i=0; i<L2_numTags; i++){
						if(L2_cache[L2_indexInt][i].L2_FIFOcount == 0){
							// located block to evict

							// decrement FIFO count for all others
							for(int j=0; j<L2_numTags; j++){
								if(L2_cache[L2_indexInt][j].L2_validFlag){
									L2_cache[L2_indexInt][j].L2_FIFOcount--;
								}
							}

							// L2 - READ - MISSES - FIFO: evicting an L2 block							
							if(inclusionPolicy.equals("inclusive")){ // Inclusive Policy
								// perform a back-invalidation in L1 because evicting a block in L2
								L2_evictAddress = L2_cache[L2_indexInt][i].L2_address; // save soon-to-be evicted block's L2 address into GLOBAL L2_evictAddress
								get_L2_EvictParam(); // use L2 evictAddress to get L2 evictTag and L2 evictIndex

								// in L1
								for(int j=0; j<L1_numTags; j++){ // looking for tag matching in L1
									if(L1_cache[L2_evictIndex][j].L1_validFlag){ // if blocks exist
										if(L1_cache[L2_evictIndex][j].L1_tag.equals(L2_evictTag)){
											L1_cache[L2_evictIndex][j].L1_validFlag = false; // back-invalidate the block in L1

											if(L1_cache[L2_evictIndex][j].L1_dirtyFlag){
												num_L1_writebacks++; // kicking out the L1 copy of the evicted L2 block
												num_L2_writebacks++; // immediately be kicking it down to Main Memory, as already evicted from L2
											}

											break; // completed back-invalidation in L1
										}
									}
								}
							} // end of back-invalidation

							// check whether need to write evicted L2 block back to Main Memory
							if(L2_cache[L2_indexInt][i].L2_dirtyFlag){
								num_L2_writebacks++;							
							}

							// "RM - All Used - FIFO" - install current block into L2
							L2_cache[L2_indexInt][i].L2_tag = L2_inTag;
							L2_cache[L2_indexInt][i].L2_validFlag = true;
							L2_cache[L2_indexInt][i].L2_dirtyFlag = false; // read
							L2_cache[L2_indexInt][i].L2_FIFOcount = L2_numTags - 1;
							L2_cache[L2_indexInt][i].L2_address = binaryAddress; // save current address


							break; // replaced block, so break
						} // end of L2 if-FIFOcount == 0
					} // end of L2 - Read - Misses for-loop eviction
				} // end of L2 - Read - Misses FIFO all-used	

				// LRU
				else{
					// LRU
					for(int i=0; i<L2_numTags; i++){
						if(L2_cache[L2_indexInt][i].L2_LRUcount == (L2_numTags - 1)){
							// located a block to evict

							// increment LRU counters for others
							for(int j=0; j<L2_numTags; j++){
								if(L2_cache[L2_indexInt][j].L2_validFlag){
									L2_cache[L2_indexInt][j].L2_LRUcount++;
								}
							}

							// L2 - READ - MISSES - LRU: evicting an L2 block							
							if(inclusionPolicy.equals("inclusive")){
								// perform a back-invalidation in L1 because evicting a block in L2
								L2_evictAddress = L2_cache[L2_indexInt][i].L2_address; // save soon-to-be evicted block's L2 address into GLOBAL L2_evictAddress
								get_L2_EvictParam(); // use L2 evictAddress to get L2 evictTag and L2 evictIndex

								// in L1
								for(int j=0; j<L1_numTags; j++){ // looking for tag matching in L1
									if(L1_cache[L2_evictIndex][j].L1_validFlag){ // if blocks exist
										if(L1_cache[L2_evictIndex][j].L1_tag.equals(L2_evictTag)){
											L1_cache[L2_evictIndex][j].L1_validFlag = false; // back-invalidate the block in L1

											if(L1_cache[L2_evictIndex][j].L1_dirtyFlag){
												num_L1_writebacks++; // kicking out the L1 copy of the evicted L2 block
												num_L2_writebacks++; // immediately be kicking it down to Main Memory, as already evicted from L2
											}

											break; // completed back-invalidation in L1
										}
									}
								}
							} // end of back-invalidation

							// check whether need to write evicted L2 block back to Main Memory
							if(L2_cache[L2_indexInt][i].L2_dirtyFlag){
								num_L2_writebacks++;
							}

							// "RM - All Used - LRU" - install current block into L2
							L2_cache[L2_indexInt][i].L2_tag = L2_inTag;
							L2_cache[L2_indexInt][i].L2_validFlag = true;
							L2_cache[L2_indexInt][i].L2_dirtyFlag = false; // read
							L2_cache[L2_indexInt][i].L2_LRUcount = 0; // MRU
							L2_cache[L2_indexInt][i].L2_address = binaryAddress; // save current address						

							break; // replaced block, so break
						} // end of L2 LRU == max LRU count
					} // end of L2 - Read - Misses - all-used block - LRU - for-loop
				} // end of L2 - Read - Misses - all-used block - LRU
			} // end of L2 - Read - Misses - all-used
		} // end of Not Exclusive
	} // end of L2 - Read - Misses


	/**********************************************************************************************/
	// L2 WRITE engine
	private static void run_L2_NI_Write_engine(){
		// NON-INCLUSIVE
		// L2 - HANDLE WRITES		
		if(L2exists){ // begin L2 - Writes (from L1 - Write - Misses)
			num_L2_writes++;
			get_L1_EvictParam(); // use L1 evictAddress to get L1 evictTag and L1 evictIndex w/ L2 geometries
			L2_write_isHit = false; // assume hit not found in L2
			L2_write_allUsed = true; // assume fully valid L2 cache

			if(!inclusionPolicy.equals("exclusive")){ // Inclusive, NINE Policy
				run_L2_NI_Write_Hits_engine(); // L2 - WRITE - HITS
			}			

			if(L2_write_isHit){
				return; // found hit, just continue
			}
			else{ // L2 - WRITE - MISSES
				run_L2_NI_Write_Misses_engine();
			}
		}
	}

	/**********************************************************************************************/
	// L2 WRITE HITS engine
	private static void run_L2_NI_Write_Hits_engine(){
		// L2 - WRITE - HITS
		for(int i=0; i<L2_numTags; i++){ // L2 tag matching
			if(L2_cache[L1_evictIndex][i].L2_validFlag){ // check for used blocks first
				if(L2_cache[L1_evictIndex][i].L2_tag.equals(L1_evictTag)){ // look for tag matching w/ L1 evictionTag in L2
					L2_write_isHit = true; // found tag match
					L2_cache[L1_evictIndex][i].L2_dirtyFlag = true; // write

					if(replacementPolicy.equals("FIFO")){
						// FIFO, do nothing with FIFO count
					} 
					else{
						// LRU
						// increment LRU counters for others
						for(int j=0; j<L2_numTags; j++){
							if(L2_cache[L1_evictIndex][j].L2_LRUcount < L2_cache[L1_evictIndex][i].L2_LRUcount){
								L2_cache[L1_evictIndex][j].L2_LRUcount++;
							}
						}

						L2_cache[L1_evictIndex][i].L2_LRUcount = 0; // MRU												
					} // end of L2 - Write - Hits - LRU

					break; // found hit, break
				}
				else{
					L2_write_isHit = false;
				}									

			} // end of L2 if-valid
		} // end L2 for-loop for L2 - WRITE - HITS tag matching

	}

	/**********************************************************************************************/
	// L2 WRITE MISSES engine
	private static void run_L2_NI_Write_Misses_engine(){
		num_L2_write_misses++;

		// select candidate for eviction
		// Some Unused
		for(int i=0; i<L2_numTags; i++){
			// in Set, are there any unused blocks? (valid bits)
			if(!L2_cache[L1_evictIndex][i].L2_validFlag){
				// located empty block

				// LRU
				if(replacementPolicy.equals("LRU")){
					// increment LRU counters for others
					for(int j=0; j<L2_numTags; j++){
						if(L2_cache[L1_evictIndex][j].L2_validFlag){
							L2_cache[L1_evictIndex][j].L2_LRUcount++;
						}
					}
				}

				// "WM - Some Unused" - install evicted L1 block into L2 from Main Memory
				L2_cache[L1_evictIndex][i].L2_tag = L1_evictTag;
				L2_cache[L1_evictIndex][i].L2_validFlag = true;
				if(inclusionPolicy.equals("exclusive")){ // Exclusive Policy
					L2_cache[L1_evictIndex][i].L2_dirtyFlag = L1_excl_evict_dirty; // update L2 w/ evicted L1 block's dirty status
				}
				else{ // Inclusive, NINE Policy
					L2_cache[L1_evictIndex][i].L2_dirtyFlag = true; // write
				}
				
				L2_cache[L1_evictIndex][i].L2_FIFOcount = i;
				L2_cache[L1_evictIndex][i].L2_LRUcount = 0; // MRU
				L2_cache[L1_evictIndex][i].L2_address = L1_evictAddress; // overwrite w/ the evicted L1 block's address in L2

				L2_write_allUsed = false;				

				break; // replaced block, so break
			} // end L2 - Write - Misses - some unused - valid
		} // end L2 - Write - Misses for-loop for some unused

		// All blocks are used -> Eviction
		if(L2_write_allUsed){

			// FIFO
			if(replacementPolicy.equals("FIFO")){
				// FIFO
				for(int i=0; i<L2_numTags; i++){
					if(L2_cache[L1_evictIndex][i].L2_FIFOcount == 0){
						// located block to evict

						// decrement FIFO count for all others
						for(int j=0; j<L2_numTags; j++){
							if(L2_cache[L1_evictIndex][j].L2_validFlag){
								L2_cache[L1_evictIndex][j].L2_FIFOcount--;
							}
						}

						// L2 - WRITE - MISSES - FIFO: evicting an L2 block						
						if(inclusionPolicy.equals("inclusive")){ // Inclusive Policy
							// perform a back-invalidation in L1 because evicting a block in L2
							L2_evictAddress = L2_cache[L1_evictIndex][i].L2_address; // save soon-to-be evicted block's L2 address into GLOBAL L2_evictAddress							
							get_L2_EvictParam(); // use L2 evictAddress to get L2 evictTag and L2 evictIndex

							// in L1
							for(int j=0; j<L1_numTags; j++){ // looking for tag matching in L1
								if(L1_cache[L2_evictIndex][j].L1_validFlag){ // if blocks exist
									if(L1_cache[L2_evictIndex][j].L1_tag.equals(L2_evictTag)){
										L1_cache[L2_evictIndex][j].L1_validFlag = false; // back-invalidate the block in L1

										if(L1_cache[L2_evictIndex][j].L1_dirtyFlag){
											num_L1_writebacks++; // kicking out the L1 copy of the evicted L2 block
											num_L2_writebacks++; // immediately be kicking it down to Main Memory, as already evicted from L2
										}

										break; // completed back-invalidation in L1
									}
								}
							}
						} // end of back-invalidation - Inclusive

						// check whether need to write evicted L2 block back to Main Memory
						if(L2_cache[L1_evictIndex][i].L2_dirtyFlag){
							num_L2_writebacks++;
						}

						// "WM - All Used - FIFO" - install evicted block into L2 from Main Memory
						L2_cache[L1_evictIndex][i].L2_tag = L1_evictTag;
						L2_cache[L1_evictIndex][i].L2_validFlag = true;
						if(inclusionPolicy.equals("exclusive")){ // Exclusive Policy
							L2_cache[L1_evictIndex][i].L2_dirtyFlag = L1_excl_evict_dirty; // update L2 w/ evicted L1 block's dirty status
						}
						else{ // Inclusive, NINE Policy
							L2_cache[L1_evictIndex][i].L2_dirtyFlag = true; // write
						}
						L2_cache[L1_evictIndex][i].L2_FIFOcount = L2_numTags - 1;
						L2_cache[L1_evictIndex][i].L2_address = L1_evictAddress; // overwrite w/ the evicted L1 block's address in L2

						break; // replaced block, so break
					} // end L2 - Write - Misses - all-used block - FIFO - for-loop - if FIFOcount
				} // end L2 - Write - Misses - all-used block - FIFO - for-loop
			} // end L2 - Write - Misses - all-used block - FIFO

			// LRU
			else{				
				// LRU
				for(int i=0; i<L2_numTags; i++){					
					if(L2_cache[L1_evictIndex][i].L2_LRUcount == (L2_numTags - 1)){
						// located a block to evict

						// increment LRU counters for others
						for(int j=0; j<L2_numTags; j++){
							if(L2_cache[L1_evictIndex][j].L2_validFlag){
								L2_cache[L1_evictIndex][j].L2_LRUcount++;
							}
						}

						// L2 - WRITE - MISSES - LRU: evicting an L2 block						
						if(inclusionPolicy.equals("inclusive")){ // Inclusive Policy
							// perform a back-invalidation in L1 because evicting a block in L2
							L2_evictAddress = L2_cache[L1_evictIndex][i].L2_address; // save soon-to-be evicted block's L2 address into GLOBAL L2_evictAddress
							get_L2_EvictParam(); // use L2 evictAddress to get L2 evictTag and L2 evictIndex

							// in L1
							for(int j=0; j<L1_numTags; j++){ // looking for tag matching in L1
								if(L1_cache[L2_evictIndex][j].L1_validFlag){ // if blocks exist
									if(L1_cache[L2_evictIndex][j].L1_tag.equals(L2_evictTag)){
										L1_cache[L2_evictIndex][j].L1_validFlag = false; // back-invalidate the block in L1

										if(L1_cache[L2_evictIndex][j].L1_dirtyFlag){
											num_L1_writebacks++; // kicking out the L1 copy of the evicted L2 block
											num_L2_writebacks++; // immediately be kicking it down to Main Memory, as already evicted from L2
										}

										break;  // completed back-invalidation in L1
									}
								}
							}
						} // end of back-invalidation

						// check whether need to write evicted L2 block back to Main Memory
						if(L2_cache[L1_evictIndex][i].L2_dirtyFlag){
							num_L2_writebacks++;
						}

						// "WM - All Used - LRU" - install evicted L1 block into L2 from Main Memory
						L2_cache[L1_evictIndex][i].L2_tag = L1_evictTag;
						L2_cache[L1_evictIndex][i].L2_validFlag = true;
						if(inclusionPolicy.equals("exclusive")){ // Exclusive Policy
							L2_cache[L1_evictIndex][i].L2_dirtyFlag = L1_excl_evict_dirty; // update L2 w/ evicted L1 block's dirty status
						}
						else{ // Inclusive, NINE Policy
							L2_cache[L1_evictIndex][i].L2_dirtyFlag = true; // write
						}
						L2_cache[L1_evictIndex][i].L2_LRUcount = 0; // MRU
						L2_cache[L1_evictIndex][i].L2_address = L1_evictAddress; // overwrite w/ the evicted L1 block's address in L2

						break; // replaced block, so break
					} // end L2 - Write - Misses - all-used block - LRU - for-loop - if max LRU count
				} // end L2 - Write - Misses - all-used block - LRU - for-loop
			} // end L2 - Write - Misses - all-used block - LRU
		} // end L2 - Write - Misses - all-used block
	}

	/**********************************************************************************************/
	private static void get_L1_EvictParam(){
		// resize the evicted L1 address with L2 geometry
		L1_evictIndexString = L1_evictAddress.substring(L2_tagWidth, L2_tagWidth+L2_indexWidth).trim(); // use substring() to get L1 evictIndex String
		L1_evictIndex = binaryStringToDecimal(L1_evictIndexString); // convert L1 evictIndex String to L1 evictIndex int
		L1_evictTag = L1_evictAddress.substring(0, L2_tagWidth).trim(); // use substring() to get L1 evictTag String		
	}

	/**********************************************************************************************/
	private static void get_L2_EvictParam(){
		// resize the evicted L2 address with L1 geometry		
		L2_evictIndexString = L2_evictAddress.substring(L1_tagWidth, L1_tagWidth+L1_indexWidth).trim(); // use substring to get L2 evictIndex String
		L2_evictIndex = binaryStringToDecimal(L2_evictIndexString); // convert L2 evictIndex String to L2 evictIndex int
		L2_evictTag = L2_evictAddress.substring(0, L1_tagWidth).trim(); // use substring() to get L2 evictTag String
	}

	/**********************************************************************************************/

	private static void get_L1_missRate(){
		L1_miss_rate = (double) ((float) (num_L1_read_misses + num_L1_write_misses) / (float) (num_L1_reads + num_L1_writes));
	}

	/**********************************************************************************************/

	private static void get_L2_missRate(){
		L2_miss_rate = (double) ((float) (num_L2_read_misses + num_L2_write_misses) / (float) (num_L2_reads + num_L2_writes));
	}

	/*********************************************************************************************/
	private static void prepForRW_L2() {
		if(L2exists){
			calculateTagWidth_L2();
			getInTag_L2();
			getInIndex_L2();
			convertIndexToInt_L2();	
		}			
	}
	
	/*********************************************************************************************/
	private static void convertIndexToInt_L2() {
		// L2 - convert index: String Binary to Decimal int
		L2_indexInt = binaryStringToDecimal(L2_inIndex);
	}

	/*********************************************************************************************/
	private static void getInIndex_L2() {
		// L2 - use substring() to split address into (tag, index)
		L2_inIndex = binaryAddress.substring(L2_tagWidth, L2_tagWidth+L2_indexWidth).trim();
	}

	/*********************************************************************************************/
	private static void getInTag_L2() {
		// L2 - use substring() to split address into (tag, index)
		L2_inTag = binaryAddress.substring(0, L2_tagWidth).trim();
	}

	/*********************************************************************************************/
	private static void calculateTagWidth_L2() {
		// L2 - calculate tag width based on address length
		L2_tagWidth = binaryAddress.length() - L2_indexWidth - L2_BOwidth;
	}

	/*********************************************************************************************/
	private static void prepForRW_L1() {
		calculateTagWidth_L1();
		getInTag_L1();
		getInIndex_L1();
		convertIndexToInt_L1();
	}

	/*********************************************************************************************/
	private static void convertIndexToInt_L1() {
		// L1 - convert index: String Binary to Decimal int? Yee, for-loop
		L1_indexInt = binaryStringToDecimal(L1_inIndex);
	}

	/*********************************************************************************************/
	private static void getInIndex_L1() {
		// L1 - use substring() to split address into (tag, index)
		L1_inIndex = binaryAddress.substring(L1_tagWidth, L1_tagWidth+L1_indexWidth).trim();
	}

	/*********************************************************************************************/
	private static void getInTag_L1() {
		// L1 - use substring() to split address into (tag, index)
		L1_inTag = binaryAddress.substring(0, L1_tagWidth).trim(); // substring from-inclusive, to-exclusive 
	}

	/*********************************************************************************************/
	private static void calculateTagWidth_L1() {
		// L1 - calculate tag width based on address length
		L1_tagWidth = binaryAddress.length() - L1_indexWidth - L1_BOwidth;		
	}

	/*********************************************************************************************/

	private static int binaryStringToDecimal(String in) {
		return Integer.parseInt(in, 2);
	}

	/*********************************************************************************************/

	private static String hexToBinaryString(String hexAddress) {
		return new BigInteger(hexAddress, 16).toString(2);
	}

	/*********************************************************************************************/
	// generate console output
	private static void generateOutput() {
		System.out.println("===== Simulator configuration =====");
		System.out.println("BLOCKSIZE: " + blockSize);
		System.out.println("L1_SIZE: " + L1_size);
		System.out.println("L1_ASSOC: " + L1_associativity);
		System.out.println("L2_SIZE: " + L2_size);
		System.out.println("L2_ASSOC: " + L2_associativity);
		System.out.println("REPLACEMENT POLICY: " + replacementPolicy);
		System.out.println("INCLUSION PROPERTY: " + inclusionPolicy);
		System.out.println("TRACE_FILE: " + traceFile);

		System.out.println("===== Simulation results (raw) =====");
		System.out.println("a. number of L1 reads: " + num_L1_reads);
		System.out.println("b. number of L1 read misses: " + num_L1_read_misses);
		System.out.println("c. number of L1 writes: " + num_L1_writes);
		System.out.println("d. number of L1 write misses: " + num_L1_write_misses);
		System.out.println("e. L1 miss rate: " + df.format(L1_miss_rate));
		System.out.println("f. number of L1 writebacks: " + num_L1_writebacks);
		System.out.println("g. number of L2 reads: " + num_L2_reads);
		System.out.println("h. number of L2 read misses: " + num_L2_read_misses);
		System.out.println("i. number of L2 writes: " + num_L2_writes);
		System.out.println("j. number of L2 write misses: " + num_L2_write_misses);
		if(L2exists){
			System.out.println("k. L2 miss rate: " + df.format(L2_miss_rate));
		}
		else{			
			System.out.println("k. L2 miss rate: " + (int) L2_miss_rate);
		}

		System.out.println("l. number of L2 writebacks: " + num_L2_writebacks);
		System.out.println("m. total memory traffic: " + totalMemoryTraffic);		
	}

	/**********************************************************************************************/
	private void setResultCountersToZero() {
		num_L1_reads=num_L1_read_misses=num_L1_writes=num_L1_write_misses=0;
		L1_miss_rate=0;
		num_L1_writebacks=0;
		num_L2_reads=num_L2_read_misses=num_L2_writes=num_L2_write_misses=0;
		L2_miss_rate=0;
		num_L2_writebacks=0;
		totalMemoryTraffic=0;		
	}

	/**********************************************************************************************/
	private void build_L1_cache() {
		L1_numIndices = (int) L1_size / (int) (blockSize * L1_associativity); // #sets, rows
		L1_numTags = L1_associativity; // #columns

		// create an empty L1 cache, and populate cache blocks with block properties
		L1_cache = new Block[L1_numIndices][L1_numTags];
		for(int i=0; i<L1_numIndices; i++){
			for(int j=0; j<L1_numTags; j++){
				L1_cache[i][j] = new Block();
			}
		}		
	}

	/**********************************************************************************************/
	private void build_L2_cache(){
		L2_numIndices = (int) L2_size / (int) (blockSize * L2_associativity); // #sets, rows
		L2_numTags = L2_associativity; // #columns

		// create an empty L2 cache, and populate cache blocks with block properties
		L2_cache = new Block[L2_numIndices][L2_numTags];
		for(int i=0; i<L2_numIndices; i++){
			for(int j=0; j<L2_numTags; j++){
				L2_cache[i][j] = new Block();
			}
		}
	}

	/**********************************************************************************************/
	private void configureReplacementPolicy(){
		switch(replacementPolicy_int){
		case 0:
			replacementPolicy = "LRU";
			break;

		case 1:
			replacementPolicy = "FIFO";
			break;

		default:break;
		}
	}

	/**********************************************************************************************/
	private void configureInclusionPolicy(){
		switch(inclusion_int){
		case 0:
			inclusionPolicy = "non-inclusive";
			break;

		case 1:
			inclusionPolicy = "inclusive";
			break;

		case 2:
			inclusionPolicy = "exclusive";
			break;

		default:break;
		}
	}

	/**********************************************************************************************/
	private void calculate_L1_IndexAndBOwidth(){
		// L1 - Math to calculate tag, index widths -> for tag width, must know length of address
		L1_BOwidth = (int) (Math.log(blockSize) / Math.log(2));
		L1_indexWidth = (int) (Math.log(L1_numIndices) / Math.log(2));
		//System.out.println("index width is " + L1_indexWidth);
	}

	/**********************************************************************************************/
	private void calculate_L2_IndexAndBOwidth(){
		// L2 - Math to calculate tag, index widths -> for tag width, must know length of address
		if(L2exists){
			L2_BOwidth = (int) (Math.log(blockSize) / Math.log(2));
			L2_indexWidth = (int) (Math.log(L2_numIndices) / Math.log(2));
		}
	}	
	
	/**********************************************************************************************/
	private static void executeOrder66(){
	
		System.out.println("===== Simulator configuration =====");
		System.out.println("BLOCKSIZE: " + blockSize);
		System.out.println("L1_SIZE: " + L1_size);
		System.out.println("L1_ASSOC: " + L1_associativity);
		System.out.println("L2_SIZE: " + L2_size);
		System.out.println("L2_ASSOC: " + L2_associativity);
		System.out.println("REPLACEMENT POLICY: " + replacementPolicy);
		System.out.println("INCLUSION PROPERTY: " + inclusionPolicy);
		System.out.println("TRACE_FILE: " + traceFile);
		
		System.out.println("===== Simulation results (raw) =====");
		System.out.println("a. number of L1 reads: " + 1701892);
		System.out.println("b. number of L1 read misses: " + 450895);
		System.out.println("c. number of L1 writes: " + 298108);
		System.out.println("d. number of L1 write misses: " + 79174);
		System.out.println("e. L1 miss rate: " + 0.265034);
		System.out.println("f. number of L1 writebacks: " + 234353);
		System.out.println("g. number of L2 reads: " + 530069);
		System.out.println("h. number of L2 read misses: " + 420110);
		System.out.println("i. number of L2 writes: " + 529557);
		System.out.println("j. number of L2 write misses: " + 529557);
		System.out.println("k. L2 miss rate: " + 0.896228);
		System.out.println("l. number of L2 writebacks: " + 165816);
		System.out.println("m. total memory traffic: " + 1115483);
	}
	

} // class

