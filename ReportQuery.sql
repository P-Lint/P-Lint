SELECT
  gcl.app as app_name ,
  gcl.commit_sha as commit_guid,
  file_path as filename,
  method_name as methodname,
  gcl.author_name as author_email,
  case Count(case when usecase_id=1 then usecase_id end) when 0 then '-' else status end AS 'Use Case 1',
  case Count(case when usecase_id=2 then usecase_id end) when 0 then '-' else status end AS 'Use Case 2',
  case Count(case when usecase_id=3 then usecase_id end) when 0 then '-' else status end AS 'Use Case 3',
  case Count(case when usecase_id=4 then usecase_id end) when 0 then '-' else status end AS 'Use Case 4',
  case Count(case when usecase_id=5 then usecase_id end) when 0 then '-' else status end AS 'Use Case 5',
  case Count(case when usecase_id=6 then usecase_id end) when 0 then '-' else status end AS 'Use Case 6',
  case Count(case when usecase_id=7 then usecase_id end) when 0 then '-' else status end AS 'Use Case 7',
  case Count(case when usecase_id=8 then usecase_id end) when 0 then '-' else status end AS 'Use Case 8',
  case Count(case when usecase_id=9 then usecase_id end) when 0 then '-' else status end AS 'Use Case 9',
  case Count(case when usecase_id=10 then usecase_id end) when 0 then '-' else status end AS 'Use Case 10',
  case Count(case when usecase_id=11 then usecase_id end) when 0 then '-' else status end AS 'Use Case 11',
  case Count(case when usecase_id=12 then usecase_id end) when 0 then '-' else status end AS 'Use Case 12',
  case Count(case when usecase_id=13 then usecase_id end) when 0 then '-' else status end AS 'Use Case 13',
  case Count(case when usecase_id=14 then usecase_id end) when 0 then '-' else status end AS 'Use Case 14',
  case Count(case when usecase_id=15 then usecase_id end) when 0 then '-' else status end AS 'Use Case 15',
  case Count(case when usecase_id=16 then usecase_id end) when 0 then '-' else status end AS 'Use Case 16'
FROM
  git_commit_log gcl
  Left outer Join report_log  rl
    on gcl.app =rl.app_name and rl.commit_id=gcl.commit_sha
GROUP BY gcl.app, gcl.commit_sha,gcl.author_name,file_path,method_name
order by gcl.app, gcl.author_date_ticks,file_path,method_name