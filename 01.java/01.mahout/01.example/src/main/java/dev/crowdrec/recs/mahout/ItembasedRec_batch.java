package dev.crowdrec.recs.mahout;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.model.file.FileDataModel;
import org.apache.mahout.cf.taste.impl.recommender.GenericItemBasedRecommender;
import org.apache.mahout.cf.taste.impl.similarity.PearsonCorrelationSimilarity;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.apache.mahout.cf.taste.recommender.Recommender;
import org.apache.mahout.cf.taste.similarity.ItemSimilarity;

public class ItembasedRec_batch {
	
	private static final String OUTMSG_READY = "READY";
	private static final String OUTMSG_OK = "OK";
	private static final String OUTMSG_KO = "KO";

	private static final String RECOMMEND_CMD = "RECOMMEND";
	private static final String TRAIN_CMD = "TRAIN";
	private static final String READINPUT_CMD = "READ_INPUT";
	private static final String STOP_CMD = "STOP";
	
	private static final String READINPUT_RELATIONS = "relations";
	private static final String READINPUT_ENTITIES = "entities";
	
	private static final String CMD_OUT_FILENAME = "cmd_out.msg";
	private static final String CMD_IN_FILENAME = "cmd_in.msg";
	
	private static final String TMP_MAHOUT_USERRATINGS_FILENAME = "mahout_ratings.csv";
	private static final boolean INPUT_FILE_HAS_HEADER = true;
	
	private static final long SLEEP_MSECS = 5000l;
	
	private String stagedir = null;
	private String commdir = null;
	
	/**
	 * 
	 * @param args
	 * $0: stage directory: directory where the algorithm can persist data (e.g., temp files, models,..)
	 * $1: communication directory: directory reserved to communication messages
	 * @throws IOException 
	 * @throws NumberFormatException 
	 * @throws TasteException 
	 */
	public static void main(String[] args) throws NumberFormatException, IOException, TasteException {
		
		if ( args.length < 2 ) {
			System.out.println("missing parameters");
			return;
		}
		
		String outdir = args[0];
		String communicationdir = args[1];
		
		ItembasedRec_batch ubr = new ItembasedRec_batch(outdir,communicationdir);
		ubr.run();
	}
	
	public ItembasedRec_batch(String stagedir, String commdir) throws IOException {
		this.stagedir = stagedir;
		this.commdir = commdir;
		
		FileWriter writer = null;
		File msg_out = new File(commdir + File.separator + CMD_OUT_FILENAME);
		try {
			writer = new FileWriter(msg_out);
			writer.write(OUTMSG_READY);
		} finally {
			if ( writer != null ) {
				writer.close();
			}
		}
		System.out.println("ALGO: machine started");
	}
	
	public void run() throws IOException, TasteException {
		Recommender recommender = null;
		boolean stop = false;
		while ( !stop ) {
			File msg_in = new File(commdir + File.separator + CMD_IN_FILENAME);
			File msg_out = new File(commdir + File.separator + CMD_OUT_FILENAME);
			if ( msg_in.exists() ) {
				try {
					Thread.sleep(SLEEP_MSECS); // wait some seconds for the file to be written
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				
				BufferedReader msgreader = null;
				FileWriter writer = null;
				try {
					msgreader = new BufferedReader(new FileReader(msg_in));
					String command = msgreader.readLine();
					if ( READINPUT_CMD.equals(command) ) {
						System.out.println("ALGO: running READ INPUT cmd");
						boolean success = cmdReadinput(msgreader);
						try {
							System.out.println(success ? "ALGO: input correctly read" : "ALGO: failing input read");
							writer = new FileWriter(msg_out);
							writer.write(success ? OUTMSG_OK : OUTMSG_KO);
						} finally {
							if ( writer != null ) {
								writer.close();
							}
						}
					} else if (TRAIN_CMD.equals(command) ) {
						System.out.println("ALGO: running TRAIN cmd");
						try {
							recommender = createRecommender(stagedir + File.separator + TMP_MAHOUT_USERRATINGS_FILENAME);
							try {
								System.out.println("ALGO: recommender created");
								writer = new FileWriter(msg_out);
								writer.write(OUTMSG_OK);
							} finally {
								if ( writer != null ) {
									writer.close();
								}
							}
						} catch (TasteException e) {
							try {
								writer = new FileWriter(msg_out);
								writer.write(OUTMSG_KO);
								e.printStackTrace();
							} finally {
								if ( writer != null ) {
									writer.close();
								}
							}
						}
					} else if (RECOMMEND_CMD.equals(command)) {
						System.out.println("ALGO: running RECOMMEND cmd");
						boolean success = (recommender != null && cmdRecommend(msgreader, recommender));
						try {
							System.out.println(success ? "ALGO: recommedation completed correctly" : "ALGO: failure in generating recommendations");
							writer = new FileWriter(msg_out);
							writer.write(success ? OUTMSG_OK : OUTMSG_KO);
						} finally {
							if ( writer != null ) {
								writer.close();
							}
						}
					} else if (STOP_CMD.equals(command)) {
						try {
							writer = new FileWriter(msg_out);
							writer.write(OUTMSG_OK);
							stop = true;
						} finally {
							if ( writer != null ) {
								writer.close();
							}
						}
					}
				} finally {
					if ( msgreader != null ) {
						msgreader.close();
					}
					if ( writer != null ) {
						writer.close();
					}
				}
				msg_in.delete();
			} else {
				try {
					Thread.sleep(SLEEP_MSECS);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		System.out.println("shutdown");
	}
	
	protected boolean cmdRecommend(BufferedReader reader, Recommender recommender) throws IOException, TasteException {
		String out_filename = reader.readLine();
		BufferedWriter writer =  null;
		try {
			File writer_file = new File( commdir + File.separator + out_filename);
			writer = new BufferedWriter(new FileWriter(writer_file));
			String line = null;
			while ((line=reader.readLine())!=null) {
				int userid = Integer.parseInt(line);
				writer.append("BEGIN user " + userid + "\n");
				List<RecommendedItem> reclist = recommender.recommend(userid, 5);
				if ( reclist != null && reclist.size() > 0 ) {
					for ( RecommendedItem item : reclist ) {
						writer.append(item.toString() + "\n");
					}
				}
				writer.append("END user " + userid + "\n");
			}
		} finally {
			if ( writer != null ) {
				writer.close();
			}
		}
		return true;
	}
	
	protected boolean cmdReadinput(BufferedReader reader) throws IOException {
		String entities_filename = null;
		String relations_filename = null;
		String line = null;
		while ((line=reader.readLine())!=null) {
			String[] els = line.split("=");
			if ( els.length == 2 ) {
				String type = els[0].trim();
				String val = els[1].trim();
				if ( READINPUT_ENTITIES.equals(type) ) 
					entities_filename = val;
				if ( READINPUT_RELATIONS.equals(type) )
					relations_filename = val;
			}
		}
		if ( entities_filename != null && relations_filename != null ) {
			convertDataset(stagedir, entities_filename, relations_filename, "user","movie","rating.explicit");
			return true;
		} else {
			// TODO: manage missing data
			return false;
		}
	}
	
	protected Recommender createRecommender(String filename) throws IOException, TasteException{
		DataModel model = new FileDataModel(new File(filename));
		ItemSimilarity similarity = new PearsonCorrelationSimilarity(model);
		Recommender recommender = new GenericItemBasedRecommender(model, similarity);
		return recommender;
	}
	
	protected void convertDataset(String outdir, String entities_filename, String relations_filename, String user_etype, String movie_etype, String rating_rtype) throws NumberFormatException, IOException {
		BufferedReader relations_reader = null;
		BufferedWriter ratings_writer = null;
		try {
			File ratings_file = new File(outdir + File.separator + TMP_MAHOUT_USERRATINGS_FILENAME);
			if ( ratings_file.exists() ) {
				ratings_file.delete();
			}
			ratings_writer = new BufferedWriter(new FileWriter(ratings_file));
			relations_reader = new BufferedReader(new FileReader(relations_filename));
			String line = (INPUT_FILE_HAS_HEADER) ? relations_reader.readLine() : null; // skip first line (if true)
			while ( (line = relations_reader.readLine()) != null ) {
				String[] els = line.split("\t");
				String rtype = els[0];
				
				if ( rtype.equals(rating_rtype) ) {
					String rid = els[1];
					long ts = Long.parseLong( els[2] );
					String props = els[3];
					String links = els[4];
					
					String userid = null;
					String itemid = null;
					double ratingscore = 0;
					
					if ( props != null ) {
						String[] els_props = props.split("::");
						for ( String el_props : els_props ) {
							String mdname = el_props.split("=")[0];
							String mdval = el_props.split("=")[1];
							if ( "rating".equals(mdname) ) {
								ratingscore = Double.parseDouble(mdval);
							}
						}
					}
					
					if ( links != null ) {
						String[] els_links = links.split("::"); 
						for ( String el_links : els_links ) {
							el_links = el_links.replaceAll("\\((.*)\\)", "$1");
							String mdname = el_links.split("=")[0];
							String mdval = el_links.split("=")[1];
							if ( mdval != null ) {
								String etype = mdval.split(":")[0];
								String eid = mdval.split(":")[1];
								if ( etype != null && eid != null ) {
									if ( "subject".equals(mdname) && etype.equals(user_etype) ) {
										userid = eid;
									} else if ( "object".equals(mdname) && etype.equals(movie_etype) ) {
										itemid = eid;
									}
								}
							}
						}
					}
					if ( userid != null && itemid != null ) {
						ratings_writer.append(userid);
						ratings_writer.append(",");
						ratings_writer.append(itemid);
						ratings_writer.append(",");
						ratings_writer.append(Double.toString(ratingscore));
						ratings_writer.append("\n");
					}
				}
			}
		} finally {
			if ( relations_reader != null ) {
				relations_reader.close();
			}
			if ( ratings_writer != null ) {
				ratings_writer.close();
			}
		}
		
	}
}
