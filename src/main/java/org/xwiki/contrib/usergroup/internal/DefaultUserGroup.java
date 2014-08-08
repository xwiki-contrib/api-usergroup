/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.usergroup.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.usergroup.UserGroup;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.user.api.XWikiGroupService;

/**
 * Implementation of a <tt>HelloWorld</tt> component.
 */
@Component
@Singleton
public class DefaultUserGroup implements UserGroup
{
    @Inject
    private DocumentReferenceResolver<String> defaultStringDocRefResolver;
	
    @Inject
    private EntityReferenceSerializer<String> stringSerializer;
    
    @Inject
    private Logger logger;
    
    @Inject
    private Provider<XWikiContext> contextProvider;
    
    @Override
	public Collection<String> getGroups(DocumentReference userOrGroup, boolean recursive, boolean localGroups,
	        boolean userWikiGroups) throws XWikiException
	    {
	        // use a set as a collection to make sure duplicates are not added
    	    XWikiContext xcontext = contextProvider.get();
	        Collection<String> allGroups = new HashSet<String>();
	        String localWiki = xcontext.getDatabase();
	        String userWiki = userOrGroup.getWikiReference().getName();

	        if (localGroups) {
	            allGroups.addAll(getMemberGroups(localWiki, userOrGroup, xcontext));
	        }
	        if (userWikiGroups && !localWiki.equals(userWiki)) {
	            allGroups.addAll(getMemberGroups(userWiki, userOrGroup, xcontext));
	        }

	        if (recursive) {
	            // use a set for a collection to make sure duplicates are not added
	            Collection<String> parentGroups = new HashSet<String>();
	            for (String group : allGroups) {
	                DocumentReference groupRef = defaultStringDocRefResolver.resolve(group);
	                parentGroups.addAll(getGroups(groupRef, recursive, localGroups, userWikiGroups));
	            }
	            allGroups.addAll(parentGroups);
	        }

	        return allGroups;
	    }
	private Collection<String> getMemberGroups(String wiki, DocumentReference memberReference, XWikiContext context)
	        throws XWikiException
	    {
	        XWikiGroupService groupService = context.getWiki().getGroupService(context);

	        Map<String, Collection<String>> grouplistcache = (Map<String, Collection<String>>) context.get("grouplist");
	        if (grouplistcache == null) {
	            grouplistcache = new HashMap<String, Collection<String>>();
	            context.put("grouplist", grouplistcache);
	        }

	        // the key is for the entity <code>prefixedFullName</code> in current wiki
	        String key = wiki + ":" + stringSerializer.serialize(memberReference);

	        Collection<String> tmpGroupList = grouplistcache.get(key);
	        if (tmpGroupList == null) {
	            String currentWiki = context.getDatabase();
	            try {
	                context.setDatabase(wiki);

	                Collection<DocumentReference> groupReferences =
	                    groupService.getAllGroupsReferencesForMember(memberReference, 0, 0, context);

	                tmpGroupList = new ArrayList<String>(groupReferences.size());
	                for (DocumentReference groupReference : groupReferences) {
	                    tmpGroupList.add(this.stringSerializer.serialize(groupReference));
	                }
	            } catch (Exception e) {
	                logger.error("Failed to get groups for user or group [" + stringSerializer.serialize(memberReference)
	                    + "] in wiki [" + wiki + "]", e);

	                tmpGroupList = Collections.emptyList();
	            } finally {
	                context.setDatabase(currentWiki);
	            }

	            grouplistcache.put(key, tmpGroupList);
	        }

	        return tmpGroupList;
	    }
}

