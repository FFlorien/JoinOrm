
package be.florien.joinorm.architecture;

public class DBArchitectureException extends RuntimeException {

    public DBArchitectureException(Exception ex) {
        super(ex);
    }

    public DBArchitectureException(String message, Exception ex) {
        super( message, ex);
    }

    // TODO: make a clearer message for each exception

    /**
     * 
     */
    private static final long serialVersionUID = -8899861760031839920L;

}
