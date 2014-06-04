package spins.promela.compiler.ltsmin.matrix;

import spins.promela.compiler.ltsmin.matrix.DepMatrix.DepRow;

/**
 *
 */
public class RWMatrix{

	public DepMatrix read = null;
    public DepMatrix mayWrite = null;
    public DepMatrix mustWrite = null;

    // use this class as dummy (for backward compatibility with RW matrix 
    public RWMatrix(DepMatrix read, DepMatrix mayWrite, DepMatrix mustWrite) {
        this.read = read;
        this.mayWrite = mayWrite;
        this.mustWrite = mustWrite;
    }
    
	public RWMatrix(int rows, int cols) {
		read = new DepMatrix(rows, cols);
		mayWrite = new DepMatrix(rows, cols);
		mustWrite = new DepMatrix(rows, cols);
	}

    public RWMatrix(RWMatrix org) {
        read = new DepMatrix(org.read);
        mayWrite = new DepMatrix(org.mayWrite);
        mustWrite = new DepMatrix(org.mustWrite);
    }

    public int getNrRows() {
        return read.getNrRows();
    }

    public int getNrCols() {
        return read.getNrCols();
    }

	public void incRead(int row, int col) {
	    read.setDependent(row, col);
	}

	public void incMayWrite(int row, int col) {
        mayWrite.setDependent(row, col);
    }
	
	public void incMustWrite(int row, int col) {
	    mustWrite.setDependent(row,  col);
	}
	
    public boolean isRead(int row, int col) {
        return read.isDependent(row, col);
    }

    public boolean isMayWrite(int row, int col) {
        return mayWrite.isDependent(row, col);
    }

    public boolean isMustWrite(int row, int col) {
        return mustWrite.isDependent(row, col);
    }
    
    public void clear() {
        read.clear();
        mayWrite.clear();
        mustWrite.clear();
    }

    public void orRow(int row, RWDepRow depRow) {
        read.orRow(row, depRow.read);
        mayWrite.orRow(row, depRow.mayWrite);
        mustWrite.orRow(row, depRow.mustWrite);
    }

	public RWDepRow getRow(int row) {
        return new RWDepRow(this, row);
	}

    public class RWDepRow {
        public DepRow read;
        public DepRow mayWrite;
        public DepRow mustWrite;

        public RWDepRow(RWMatrix rw, int row) {
            this.read = rw.read.getRow(row);
            this.mayWrite = rw.mayWrite.getRow(row);
            this.mustWrite = rw.mustWrite.getRow(row);
        }

        public boolean reads(RWDepRow other) {
            return read.isDependent(other.read) ||
                   read.isDependent(other.mayWrite);
        }

        public boolean reads(DepRow other) {
            return read.isDependent(other);
        }

        public boolean mayWrites(DepRow other) {
            return mayWrite.isDependent(other);
        }

        public boolean mayWrites(RWDepRow other) {
            return mayWrite.isDependent(other.read) ||
                   mayWrite.isDependent(other.mayWrite);
        }

        public boolean dependent(DepRow other) {
            return this.reads(other) ||
                   this.mayWrites(other);
        }

        public boolean dependent(RWDepRow other) {
            return this.reads(other) ||
                   this.mayWrites(other);
        }

        public int readCardinality() {
            return read.getCardinality();
        }

        public int mayWriteCardinality() {
            return mayWrite.getCardinality();
        }
        
        public int mustWriteCardinality() {
            return mustWrite.getCardinality();
        }

        public int getNrCols() {
            return read.getNrCols();
        }

        public int intRead(int col) {
            return read.isDependent(col) ? 1 : 0;
        }

        public int intMayWrite(int col) {
            return mayWrite.isDependent(col) ? 1 : 0;
        }
        
        public int intMustWrite(int col) {
            return mustWrite.isDependent(col) ? 1 : 0;
        }

        public boolean isRead(int col) {
            return read.isDependent(col);
        }

        public boolean isMayWrite(int col) {
            return mayWrite.isDependent(col);
        }
        
        public boolean isMustWrite(int col) {
            return mustWrite.isDependent(col);
        }
    }
}