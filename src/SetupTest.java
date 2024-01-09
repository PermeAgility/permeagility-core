import com.arcadedb.ContextConfiguration;
import com.arcadedb.database.DatabaseContext;
import com.arcadedb.engine.ComponentFile.MODE;
import com.arcadedb.schema.DocumentType;
import com.arcadedb.server.ArcadeDBServer;
import com.arcadedb.server.ServerDatabase;

public class SetupTest {

    public static String schemaScript = 
    """
      CREATE DOCUMENT TYPE restricted IF NOT EXISTS;
      CREATE DOCUMENT TYPE identity IF NOT EXISTS;

      CREATE DOCUMENT TYPE role IF NOT EXISTS;
      ALTER TYPE role SUPERTYPE +identity;
      CREATE DOCUMENT TYPE user IF NOT EXISTS;
      ALTER TYPE user SUPERTYPE +identity;
      CREATE DOCUMENT TYPE privilege IF NOT EXISTS;
      
      CREATE DOCUMENT TYPE constant IF NOT EXISTS;
      CREATE DOCUMENT TYPE locale IF NOT EXISTS;
      CREATE DOCUMENT TYPE message IF NOT EXISTS;
      CREATE DOCUMENT TYPE pickList IF NOT EXISTS;
      CREATE DOCUMENT TYPE tableGroup IF NOT EXISTS;
      CREATE DOCUMENT TYPE columns IF NOT EXISTS;
      CREATE DOCUMENT TYPE pickValues IF NOT EXISTS;
      CREATE DOCUMENT TYPE auditTrail IF NOT EXISTS;
      CREATE DOCUMENT TYPE thumbnail IF NOT EXISTS;
      CREATE DOCUMENT TYPE userProfile IF NOT EXISTS;

      CREATE DOCUMENT TYPE menu IF NOT EXISTS;
      CREATE DOCUMENT TYPE menuItem IF NOT EXISTS;
      ALTER TYPE menuItem SUPERTYPE +restricted;
      CREATE DOCUMENT TYPE news IF NOT EXISTS;
      ALTER TYPE news SUPERTYPE +restricted;
    """;
    public static String schemaScript2 = 
    """
      CREATE PROPERTY identity.name IF NOT EXISTS STRING;
      CREATE PROPERTY restricted._allowUpdate IF NOT EXISTS LIST OF identity;
      CREATE PROPERTY restricted._allowDelete IF NOT EXISTS LIST OF identity;
      CREATE PROPERTY restricted._allowRead IF NOT EXISTS LIST OF identity;
      CREATE PROPERTY restricted._allow IF NOT EXISTS LIST OF identity;

      CREATE PROPERTY role.mode IF NOT EXISTS STRING;
      CREATE PROPERTY role.inheritedRole IF NOT EXISTS LINK OF role;
      CREATE PROPERTY role.rules IF NOT EXISTS EMBEDDED;

      CREATE PROPERTY user.password IF NOT EXISTS STRING;
      CREATE PROPERTY user.status IF NOT EXISTS STRING;
      CREATE PROPERTY user.roles IF NOT EXISTS LIST OF role;

      CREATE PROPERTY privilege.access IF NOT EXISTS STRING;
      CREATE PROPERTY privilege.resource IF NOT EXISTS STRING;
      CREATE PROPERTY privilege.identity IF NOT EXISTS LINK OF identity;

      CREATE PROPERTY constant.classname IF NOT EXISTS STRING;
      CREATE PROPERTY constant.description IF NOT EXISTS STRING;
      CREATE PROPERTY constant.field IF NOT EXISTS STRING;
      CREATE PROPERTY constant.value IF NOT EXISTS STRING;

      CREATE PROPERTY locale.name IF NOT EXISTS STRING;
      CREATE PROPERTY locale.description IF NOT EXISTS STRING;
      CREATE PROPERTY locale.active IF NOT EXISTS BOOLEAN;

      CREATE PROPERTY message.name IF NOT EXISTS STRING;
      CREATE PROPERTY message.description IF NOT EXISTS STRING;
      CREATE PROPERTY message.locale IF NOT EXISTS LINK OF locale;

      CREATE PROPERTY pickList.tablename IF NOT EXISTS STRING;
      CREATE PROPERTY pickList.query IF NOT EXISTS STRING;
      CREATE PROPERTY pickList.description IF NOT EXISTS STRING;

      CREATE PROPERTY tableGroup.name IF NOT EXISTS STRING;
      CREATE PROPERTY tableGroup.tables IF NOT EXISTS STRING;

      CREATE PROPERTY columns.name IF NOT EXISTS STRING;
      CREATE PROPERTY columns.columnList IF NOT EXISTS STRING;

      CREATE PROPERTY menu.name IF NOT EXISTS STRING;
      CREATE PROPERTY menu.active IF NOT EXISTS BOOLEAN;
      CREATE PROPERTY menu.description IF NOT EXISTS STRING;
      CREATE PROPERTY menu.sortOrder IF NOT EXISTS INTEGER;

      CREATE PROPERTY menuItem.name IF NOT EXISTS STRING;
      CREATE PROPERTY menuItem.description IF NOT EXISTS STRING;
      CREATE PROPERTY menuItem.classname IF NOT EXISTS STRING;
      CREATE PROPERTY menuItem.type IF NOT EXISTS STRING;
      CREATE PROPERTY menuItem.active IF NOT EXISTS BOOLEAN;
      CREATE PROPERTY menuItem.pageScript IF NOT EXISTS STRING;
      CREATE PROPERTY menuItem.pageStyle IF NOT EXISTS STRING;
      CREATE PROPERTY menuItem.useStyleFrom IF NOT EXISTS LINK OF menuItem;

      CREATE PROPERTY menu.items IF NOT EXISTS LIST OF menuItem;

      CREATE PROPERTY news.name IF NOT EXISTS STRING;
      CREATE PROPERTY news.description IF NOT EXISTS STRING;
      CREATE PROPERTY news.dateline IF NOT EXISTS DATETIME;
      CREATE PROPERTY news.locale IF NOT EXISTS LINK OF locale;
      CREATE PROPERTY news.archive IF NOT EXISTS BOOLEAN;

      CREATE PROPERTY pickValues.name IF NOT EXISTS STRING;
      CREATE PROPERTY pickValues.values IF NOT EXISTS STRING;

      CREATE PROPERTY auditTrail.timestamp IF NOT EXISTS DATETIME;
      CREATE PROPERTY auditTrail.user IF NOT EXISTS STRING;
      CREATE PROPERTY auditTrail.action IF NOT EXISTS STRING;
      CREATE PROPERTY auditTrail.table IF NOT EXISTS STRING;
      CREATE PROPERTY auditTrail.rid IF NOT EXISTS STRING;
      CREATE PROPERTY auditTrail.detail IF NOT EXISTS EMBEDDED;
      CREATE INDEX IF NOT EXISTS ON auditTrail (timestamp) NOTUNIQUE;
      CREATE INDEX IF NOT EXISTS ON auditTrail (rid) NOTUNIQUE;

      CREATE PROPERTY thumbnail.name IF NOT EXISTS STRING;
      CREATE PROPERTY thumbnail.table IF NOT EXISTS STRING;
      CREATE PROPERTY thumbnail.column IF NOT EXISTS STRING;
      CREATE PROPERTY thumbnail.id IF NOT EXISTS STRING;
      CREATE PROPERTY thumbnail.type IF NOT EXISTS STRING;
      CREATE PROPERTY thumbnail.size IF NOT EXISTS LONG;
      CREATE PROPERTY thumbnail.width IF NOT EXISTS INTEGER;
      CREATE PROPERTY thumbnail.height IF NOT EXISTS INTEGER;
      CREATE PROPERTY thumbnail.small IF NOT EXISTS BINARY;
      CREATE PROPERTY thumbnail.medium IF NOT EXISTS BINARY;
      CREATE INDEX IF NOT EXISTS ON thumbnail (id) NOTUNIQUE; 

      CREATE PROPERTY userProfile.name IF NOT EXISTS STRING;
      CREATE PROPERTY userProfile.password IF NOT EXISTS STRING;
      """;

    public static void main(String[] args) {
        boolean AUTO_TRANS = false;
        if (args.length > 0 && args[0].equals("auto")) AUTO_TRANS = true;

        ContextConfiguration config = new ContextConfiguration();
        ArcadeDBServer server = null;
        ServerDatabase sdb = null;
        String url = "tdb";

        try {
            System.out.println("Starting server...");
            server = new ArcadeDBServer(config);
            server.start();
        } catch (Exception e) {
            System.out.println("Error at start server/create db "+e.getMessage());
            //e.printStackTrace();
        }
        try {
            if (!server.existsDatabase(url)) {
                sdb = server.createDatabase(url, MODE.READ_WRITE);
            } else {
                sdb = server.getDatabase(url);
            }
            if (sdb.isOpen()) System.out.println("\n\nDatabase is open");
            System.out.println("AUTO_TRANS="+AUTO_TRANS);
            System.out.println("Transactions="+DatabaseContext.INSTANCE.getContextIfExists(sdb.getDatabasePath()).transactions.size());
            if (AUTO_TRANS) sdb.setAutoTransaction(true);
            System.out.println("Transactions="+DatabaseContext.INSTANCE.getContextIfExists(sdb.getDatabasePath()).transactions.size());
        } catch (Exception e) {            
            System.out.println("Error at getDatabase "+e.getMessage());
            //e.printStackTrace();
        }

        if (sdb.isOpen()) {
            try {
                DocumentType dt = sdb.getSchema().getType("menuItem");
                DocumentType dt2 = sdb.getSchema().getType("identity");
                DocumentType dt3 = sdb.getSchema().getType("userProfile");
                System.out.println("\n\nUpdate script has already run\n\n");
            } catch (Exception e) {
                System.out.println("\nCould not find menuItem or identity: "+e.getMessage());
                System.out.println("\n\nRunning update script");
                try {
                    if (!AUTO_TRANS) sdb.begin();
                    System.out.println("Transactions="+DatabaseContext.INSTANCE.getContextIfExists(sdb.getDatabasePath()).transactions.size());
                    sdb.command("sqlscript",schemaScript);
                    if (!AUTO_TRANS) sdb.commit();
                    System.out.println("Update script complete");
                    System.out.println("Transactions="+DatabaseContext.INSTANCE.getContextIfExists(sdb.getDatabasePath()).transactions.size());
                } catch (Exception e2) {
                    System.out.println("\n\nException running update script: "+e.getMessage());
                }
            }
            try {
                if (!AUTO_TRANS) sdb.begin();
                System.out.println("Transactions="+DatabaseContext.INSTANCE.getContextIfExists(sdb.getDatabasePath()).transactions.size());
                sdb.command("sqlscript",schemaScript2);
                if (!AUTO_TRANS) sdb.commit();
            } catch (Exception e) {
                    System.out.println("\n\nException running update script2: "+e.getMessage());
            }
            //sdb.close();  // unsupported operation

        }
        server.stop();
    }
}
