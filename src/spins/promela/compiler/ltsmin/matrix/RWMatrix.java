package spins.promela.compiler.ltsmin.matrix;

import spins.promela.compiler.ltsmin.matrix.DepMatrix.DepRow;

/**
 *
 */
public class RWMatrix{

	public DepMatrix read = null;
	public DepMatrix write = null;

    // use this class as dummy (for backward compatibility with RW matrix 
    public RWMatrix(DepMatrix read, DepMatrix write) {
        this.read = read;
        this.write = write;
    }
    
	public RWMatrix(int rows, int cols) {
		read = new DepMatrix(rows, cols);
		write = new DepMatrix(rows, cols);
	}

    public RWMatrix(RWMatrix org) {
        read = new DepMatrix(org.read);
        write = new DepMatrix(org.write);
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

	public void incWrite(int row, int col) {
        write.setDependent(row, col);
    }
	
    public boolean isRead(int row, int col) {
        return read.isDependent(row, col);
    }

    public boolean isWrite(int row, int col) {
        return write.isDependent(row, col);
    }

    public void clear() {
        read.clear();
        write.clear();
    }

    public void orRow(int row, RWDepRow depRow) {
        read.orRow(row, depRow.read);
        write.orRow(row, depRow.write);
    }

	public RWDepRow getRow(int row) {
        return new RWDepRow(this, row);
	}

    public class RWDepRow {
        public DepRow read;
        public DepRow write;

        public RWDepRow(RWMatrix rw, int row) {
            this.read = rw.read.getRow(row);
            this.write = rw.write.getRow(row);
        }

        public boolean reads(RWDepRow other) {
            return read.isDependent(other.read) ||
                   read.isDependent(other.write);
        }

        public boolean reads(DepRow other) {
            return read.isDependent(other);
        }

        public boolean writes(DepRow other) {
            return write.isDependent(other);
        }

        public boolean writes(RWDepRow other) {
            return write.isDependent(other.read) ||
                   write.isDependent(other.write);
        }

        public boolean dependent(DepRow other) {
            return this.reads(other) ||
                   this.writes(other);
        }

        public boolean dependent(RWDepRow other) {
            return this.reads(other) ||
                   this.writes(other);
        }

        public int readCardinality() {
            return read.getCardinality();
        }

        public int writeCardinality() {
            return write.getCardinality();
        }

        public int getNrCols() {
            return read.getNrCols();
        }

        public int intRead(int col) {
            return read.isDependent(col) ? 1 : 0;
        }

        public int intWrite(int col) {
            return write.isDependent(col) ? 1 : 0;
        }

        public boolean isRead(int col) {
            return read.isDependent(col);
        }

        public boolean isWrite(int col) {
            return write.isDependent(col);
        }
    }
}