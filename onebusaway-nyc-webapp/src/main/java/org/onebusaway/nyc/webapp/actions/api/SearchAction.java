/**
 * Copyright (c) 2011 Metropolitan Transportation Authority
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.onebusaway.nyc.webapp.actions.api;

import org.onebusaway.nyc.geocoder.model.NycGeocoderResult;
import org.onebusaway.nyc.geocoder.service.NycGeocoderService;
import org.onebusaway.nyc.presentation.impl.sort.SearchResultComparator;
import org.onebusaway.nyc.presentation.model.search.StopResult;
import org.onebusaway.nyc.presentation.service.search.RouteSearchService;
import org.onebusaway.nyc.presentation.service.search.SearchResult;
import org.onebusaway.nyc.presentation.service.search.StopSearchService;
import org.onebusaway.nyc.webapp.actions.OneBusAwayNYCActionSupport;
import org.onebusaway.nyc.webapp.actions.api.model.DesktopWebSearchModelFactory;
import org.onebusaway.nyc.webapp.actions.api.model.DesktopWebStopResult;
import org.onebusaway.nyc.webapp.actions.m.model.MobileWebLocationResult;

import org.apache.struts2.convention.annotation.ParentPackage;
import org.apache.struts2.convention.annotation.Result;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ParentPackage("json-default")
@Result(type="json", params={"callbackParameter", "callback"})
public class SearchAction extends OneBusAwayNYCActionSupport {

  private static final long serialVersionUID = 1L;

  @Autowired
  private RouteSearchService _routeSearchService;

  @Autowired
  private StopSearchService _stopSearchService;

  @Autowired
  private NycGeocoderService _geocoderService;
  
  private List<SearchResult> _searchResults = new ArrayList<SearchResult>();
  
  private String _q;

  public void setQ(String query) {
    if(query != null)
      _q = query.trim();
    else
      _q = null;
  }

  @Override
  public String execute() {    
    if(_q == null || _q.isEmpty())
      return SUCCESS;

    DesktopWebSearchModelFactory factory = new DesktopWebSearchModelFactory();
    _stopSearchService.setModelFactory(factory);
    _routeSearchService.setModelFactory(factory);
    
    // try as stop ID
    _searchResults.addAll(_stopSearchService.resultsForQuery(_q));

    // try as route ID
    if(_searchResults.size() == 0) {
      _searchResults.addAll(_routeSearchService.resultsForQuery(_q));
    }

    // nothing? geocode it!
    if(_searchResults.size() == 0) {
      _searchResults.addAll(generateResultsFromGeocode(_q));        
    }

    transformSearchModels(_searchResults);

    Collections.sort(_searchResults, new SearchResultComparator());

    return SUCCESS;
  }   

  private void transformSearchModels(List<SearchResult> searchResults) {
    for(SearchResult searchResult : searchResults) {
      if(searchResult instanceof StopResult) {
        injectRouteData((StopResult)searchResult);
      }
    }
  }
  
  private StopResult injectRouteData(StopResult _stopSearchResult) {
    DesktopWebStopResult stopSearchResult = (DesktopWebStopResult)_stopSearchResult;
    
    stopSearchResult.setNearbyRoutes(_routeSearchService.resultsForLocation(
        stopSearchResult.getLatitude(), 
        stopSearchResult.getLongitude()));
    
    return stopSearchResult;
  }

  private List<SearchResult> generateResultsFromGeocode(String q) {
    List<SearchResult> results = new ArrayList<SearchResult>();
    
    List<NycGeocoderResult> geocoderResults = _geocoderService.nycGeocode(q);

    for(NycGeocoderResult result : geocoderResults) {
      MobileWebLocationResult locationSearchResult = new MobileWebLocationResult();
      locationSearchResult.setGeocoderResult(result);

      if(result.isRegion()) {
        locationSearchResult.setNearbyRoutes(
            _routeSearchService.resultsForLocation(result.getBounds()));
      } else {
        locationSearchResult.setNearbyRoutes(
            _routeSearchService.resultsForLocation(result.getLatitude(), result.getLongitude()));
      }
      
      results.add(locationSearchResult);
    }

    return results;
  }  
  
  public List<SearchResult> getSearchResults() {
    return _searchResults;
  }

}