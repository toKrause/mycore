/**
 * $RCSfile$
 * $Revision$ $Date$
 *
 * This file is part of ** M y C o R e **
 * Visit our homepage at http://www.mycore.de/ for details.
 *
 * This program is free software; you can use it, redistribute it
 * and / or modify it under the terms of the GNU General Public License
 * (GPL) as published by the Free Software Foundation; either version 2
 * of the License or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program, normally in the file license.txt.
 * If not, write to the Free Software Foundation Inc.,
 * 59 Temple Place - Suite 330, Boston, MA  02111-1307 USA
 *
 **/

package org.mycore.datamodel.ifs;

import org.mycore.common.*;

/**
 * Represents a read-only view of MCRFile metadata.
 *
 * @author Frank Lützenkirchen 
 * @version $Revision$ $Date$
 */
public interface MCRFileReader
{
  /**
   * Returns the ID of the owner of this file
   *
   * @return the ID of the owner of this file
   **/
  public String getOwnerID();

  /**
   * Returns the relative path of this file
   **/
  public String getPath();
  
  /**
   * Returns the file extension of this file, or an empty string if
   * the file has no extension
   **/
  public String getExtension();

  /**
   * Returns the file size as number of bytes
   **/
  public long getSize();
  
  /**
   * Returns the ID of the MCRContentStore implementation that holds the
   * content of this file
   **/
  public String getStoreID();
  
  /**
   * Returns the storage ID that identifies the place where the MCRContentStore 
   * has stored the content of this file
   **/
  public String getStorageID();
}
